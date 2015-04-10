
package crosswords.util

import java.io.File
import play.api.libs.json.{JsArray, JsObject, JsValue, Json}
import scala.collection.mutable.ArrayBuffer
import scala.io.Source

/**
 * Helper to merge multiple JSON objects into a JSON array.
 *
 * @author Timothée Emery
 */
object Packer {

  /**
   * Extract all top-level objects from this value.
   */
  def unpack(value: JsValue): Seq[JsObject] = value match {
    case obj: JsObject => Seq(obj)
    case arr: JsArray => arr.value.flatMap(unpack)
    case _ => Seq.empty
  }

  /**
   * Extract all top-level objects from these values.
   */
  def unpack(values: Seq[JsValue]): Seq[JsObject] =
    values.flatMap(unpack)

  /**
   * Pack many values into one single array.
   * Only top-level objects are kept, arrays are flatten.
   */
  def pack(values: Seq[JsValue]): JsArray =
    JsArray(unpack(values))

  /**
   * Pack JSON objects into chunks of size <code>size</code> in bytes.
   * The last chunk might be smaller than specified size.
   */
  def pack(values: TraversableOnce[JsValue], size: Int): Seq[JsValue] = {
    val result = new ArrayBuffer[JsValue]()
    var buffer = JsArray()
    for (value <- values) {
      buffer = pack(Seq(buffer, value))
      if (Json.prettyPrint(buffer).getBytes("UTF-8").length > size) {
        result += buffer
        buffer = JsArray()
      }
    }
    if (buffer.value.nonEmpty)
      result += buffer
    result
  }

  /**
   * Read all JSON files from specified directory (recursive).
   * <code>file</code> can also be a single file.
   */
  def read(file: File): Iterator[JsValue] = {
    if (file.isFile && file.getName.endsWith(".json")) {
      val text = Source.fromFile(file).mkString
      Iterator(Json.parse(text))
    } else if (file.isDirectory) {
      file.listFiles.toIterator.flatMap(read)
    } else
      Iterator.empty
  }

  /**
   * Read all JSON files from specified directory (recursive).
   * <code>file</code> can also be a single file.
   */
  def read(file: String): Iterator[JsValue] =
    read(new File(file))

  // TODO write all to specified path

}