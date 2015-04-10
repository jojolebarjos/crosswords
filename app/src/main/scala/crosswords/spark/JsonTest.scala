
package crosswords.spark

import java.io.{File, BufferedWriter, FileWriter}
import org.apache.spark.SparkContext
import JsonInputFormat._
import play.api.libs.json.JsObject

object JsonTest {

  def main(args: Array[String]) {

    // Open context
    val context = new SparkContext("local", "shell")
    //val context = new SparkContext(new SparkConf().setAppName("Crosswords"))

    // Get all crosswords
    val objects = context.jsObjectFile("../data/crosswords/*").map(_._2)

    // Compute word set
    val entries = objects.flatMap(c => (c \ "words").as[Seq[JsObject]])
    val words = entries.map(e => (e \ "word").as[String]).distinct().sortBy(w => w)

    // Save words to disk
    val array = words.collect()
    val output = new BufferedWriter(new FileWriter(new File("../data/words.txt")))
    for (word <- array)
      output.write(word + "\r\n")
    output.close()
    println(array.length + " unique words written")

  }

}
