package Module5
import org.apache.spark.sql.{DataFrame, SQLContext}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.log4j.{Level, Logger}
import org.apache.spark.rdd.RDD
import org.apache.spark.ml.recommendation.{ALS, ALSModel}
object SupervisedExample {
  val numIterations = 10
  val rank = 10 //number of features to consider when training the model
  //this file is in UserID::MovieID::Rating::Timestamp format
  val ratingsFile = "../data/als/sample_movielens_ratings.txt"
  val moviesFile = "../data/als/sample_movielens_movies.txt"
  val testFile = "../data/als/test.data"


  def main(args: Array[String]): Unit = run()
  def run(): Unit = {
    val conf = new SparkConf()
      .setAppName("SupervisedLearning")
      .setMaster("local[*]")
      .set("spark.app.id", "ALS")   // To silence Metrics warning.

    val sc = new SparkContext(conf)
    val sqlContext = new SQLContext(sc)
    import sqlContext.implicits._

    Logger.getRootLogger.setLevel(Level.ERROR)
    val ratingsData: RDD[Rating]= sc.textFile(ratingsFile).map(Rating.parseRating).cache()
    val testData: RDD[Rating]= sc.textFile(testFile).map(Rating.parseRating).cache()
    val numRatings= ratingsData.count()
    val numUsers= ratingsData.map(_.userId).distinct().count()
    val numMovies= ratingsData.map(_.movieId).distinct().count()
    println(s"got $numRatings ratings, $numMovies movies , $numUsers users")
    val als= new ALS().setUserCol("userId").setItemCol("movieId").setRank(rank).setMaxIter(numIterations)

    val model: ALSModel= als.fit(ratingsData.toDF())

    val predictions: DataFrame= model.transform(testData.toDF()).cache()

    val movies= sc.textFile(moviesFile).map(Movie.parseMovie).toDF()
     val falsePositive= predictions.join(movies).where((predictions("movieId")===movies("movieId"))  &&($"rating"<=1) && ($"prediction">=4))
       .select($"userId",predictions("movieId"),$"title",$"rating",$"prediction")
    val numFalsePositive= falsePositive.count()
    println(s"Found $numFalsePositive false positive")
    if (numFalsePositive>0){
      println("Example false positive")
      falsePositive.limit(100).collect().foreach(println)
    }
    predictions.show()
    println(">>>> Find out predictions where user 26 likes movies 10, 15, 20 & 25")
    val df26 = sc.makeRDD(Seq(26 -> 10, 26 -> 15, 26 -> 20, 26 -> 25))
      .toDF("userId", "movieId")
    model.transform(df26).show()

    sc.stop()
  }

}
case class Rating(userId: Int, movieId: Int, rating: Double)

object Rating {
  //format is: userId, movieId, rating, timestamp
  def parseRating(str: String): Rating = {
    val fields = str.split("::")
    Rating(fields(0).toInt, fields(1).toInt, fields(2).toDouble) //we don't care about the timestamp
  }
}

case class Movie(movieId: Int, title: String, genres: Seq[String])

object Movie {
  //format is: movieId, title, genre1|genre2
  def parseMovie(str: String): Movie = {
    val fields = str.split("::")
    assert(fields.size == 3)
    Movie(fields(0).toInt, fields(1), fields(2).split("|"))
  }
}

