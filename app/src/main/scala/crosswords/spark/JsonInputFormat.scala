
package crosswords.spark

import java.io.{InputStreamReader, BufferedReader}
import crosswords.util.Packer
import org.apache.hadoop.fs.Path
import org.apache.hadoop.mapreduce.{JobContext, RecordReader, TaskAttemptContext, InputSplit}
import org.apache.hadoop.mapreduce.lib.input.{FileSplit, FileInputFormat}
import org.apache.spark.SparkContext
import org.apache.spark.SparkContext.rddToPairRDDFunctions
import org.apache.spark.rdd.RDD
import play.api.libs.json.{JsObject, Json, JsValue}

/**
 * File input format used to decode JSON files.
 *
 * @author Johan Berdat
 * @author Timothée Emery
 */
class JsonInputFormat extends FileInputFormat[String, JsValue] {

  override def createRecordReader(split: InputSplit, task: TaskAttemptContext) = new JsonRecordReader()

  override def isSplitable(context: JobContext, filename: Path) = false

  class JsonRecordReader extends RecordReader[String, JsValue] {

    private var name: String = _
    private var json: JsValue = _
    private var finished = false

    override def initialize(split: InputSplit, task: TaskAttemptContext) {

      // Get input stream to JSON file
      val path = split.asInstanceOf[FileSplit].getPath
      val system = path.getFileSystem(task.getConfiguration)
      val input = system.open(path)

      // Read whole file as string
      val reader = new BufferedReader(new InputStreamReader(input))
      val builder = new StringBuilder()
      for (line <- Iterator.continually(reader.readLine()).takeWhile(_ != null)) {
        builder.append(line)
        builder.append('\n')
      }
      val text = builder.toString()
      reader.close()

      // Convert to JSON
      name = path.getName
      json = Json.parse(text)

    }

    override def nextKeyValue() = {
      if (finished)
        false
      else {
        finished = true
        true
      }
    }

    override def getProgress =
      if (finished) 1.0f else 0.0f

    override def getCurrentKey = name

    override def getCurrentValue = json

    override def close() {}

  }

}

/**
 * Extends Spark with helpers for JSON objects.
 */
object JsonInputFormat {

  implicit def extendSparkContext(context: SparkContext) = new {

    /**
     * Like <code>SparkContext.textFile</code>, open one or more JSON files, with one tuple per file.
     */
    def jsValueFile(path: String): RDD[(String, JsValue)] =
      context.newAPIHadoopFile[String, JsValue, JsonInputFormat](path)

    /**
     * Like <code>SparkContext.textFile</code>, open one or more JSON files, with one tuple per top-level JSON object.
     */
    def jsObjectFile(path: String): RDD[(String, JsObject)] =
      jsValueFile(path).flatMapValues(Packer.unpack)

  }

}
