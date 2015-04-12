
package crosswords.spark

import java.io.{File, BufferedWriter, FileWriter}
import org.apache.spark.SparkContext
import JsonInputFormat._

object JsonTest {

  def main(args: Array[String]) {

    // Open context
    val context = new SparkContext("local", "shell")
    //val context = new SparkContext(new SparkConf().setAppName("Crosswords"))

    // Get all crosswords
    val objects = context.jsObjectFile("../data/crosswords/*").map(_._2)

    // Compute word and counts
    val words = Bags.clues(objects).map(_._1).countByValue()

    // Save words to disk
    val output = new BufferedWriter(new FileWriter(new File("../data/words.txt")))
    for (word <- words.toSeq.sortBy(_._1))
      output.write(word._1 + ": " + word._2 + "\r\n")
    output.close()

  }

}
