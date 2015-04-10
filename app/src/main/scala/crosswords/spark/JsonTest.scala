
package crosswords.spark

import org.apache.spark.SparkContext
import JsonInputFormat._
import play.api.libs.json.Json

object JsonTest {

  def main(args: Array[String]) {

    val context = new SparkContext("local", "shell")

    val values = context.jsonFile("../data/sample/*.json")
    
    for ((_, v) <- values)
      println(Json.prettyPrint(v))

  }

}
