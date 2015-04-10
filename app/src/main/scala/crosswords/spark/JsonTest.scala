
package crosswords.spark

import org.apache.spark.SparkContext
import JsonInputFormat._
import play.api.libs.json.Json

object JsonTest {

  def main(args: Array[String]) {

    val context = new SparkContext("local", "shell")

    val objects = context.jsObjectFile("../data/sample/*.json")
    val urls = objects.flatMap(obj => (obj._2 \ "url").asOpt[String])

    for (url <- urls.collect())
      println(url)

  }

}
