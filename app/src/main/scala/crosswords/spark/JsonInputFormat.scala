
import java.io.{InputStreamReader, BufferedReader}
import org.apache.hadoop.fs.Path
import org.apache.hadoop.mapreduce.{JobContext, RecordReader, TaskAttemptContext, InputSplit}
import org.apache.hadoop.mapreduce.lib.input.{FileSplit, FileInputFormat}
import play.api.libs.json.{JsArray, Json, JsObject, JsValue}


/**
 * File input format used to decode JSON files.
 * This abstract class must be subclassed to implement the conversion between JsObject and String key-values.
 *
 * @author Johan Berdat
 * @author Timothée Emery
 */
abstract class JsonInputFormat extends FileInputFormat[String, String] {

  override def createRecordReader(split: InputSplit, task: TaskAttemptContext) = new JsonRecordReader()

  override def isSplitable(context: JobContext, filename: Path) = false

  def transform(obj: JsObject): Seq[(String, String)]

  class JsonRecordReader extends RecordReader[String, String] {

    private var pairs: Seq[(String, String)] = Nil
    private var index = -1

    def convert(value: JsValue) = value match {
      case obj : JsObject => transform(obj)
      case _ => Nil
    }

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
      val json = Json.parse(text)

      // Parse JSON
      pairs = json match {
        case arr : JsArray => arr.value.flatMap(convert)
        case _ => convert(json)
      }

    }

    override def getProgress =
      if (pairs.isEmpty) 1.0f else (index + 1).toFloat / (pairs.size + 1).toFloat

    override def nextKeyValue() = {
      index += 1
      index < pairs.size
    }

    override def getCurrentKey = pairs(index)._1

    override def getCurrentValue = pairs(index)._2

    override def close() {}

  }

}


/**
 * JSON input format for crossword files.
 *
 * @author Johan Berdat
 * @author Timothée Emery
 */
class CrosswordInputFormat extends JsonInputFormat {

  override def transform(obj: JsObject): Seq[(String, String)] = {
    val array = (obj \ "words").asInstanceOf[JsArray].value
    array.map(w => (w \ "word").as[String] -> (w \ "clue").as[String])
  }

}

// TODO input format for definitions JSON