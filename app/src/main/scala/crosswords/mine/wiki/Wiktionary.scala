
package crosswords.mine.wiki

import java.io._
import crosswords.util.{Parallel, Packer}
import org.apache.commons.compress.compressors.CompressorStreamFactory
import play.api.libs.json.{Json, JsObject}

/**
 * Convert Wiktionary dump file into JSON definitions.
 *
 * @author Johan Berdat
 * @author Laurent Valette
 * @author Vincent Mettraux
 */
object Wiktionary {

  def extract(title: String, page: String): Seq[JsObject] = {

    // Get english section
    val markup = Markup(page)
    for (english <- Helper.headers(markup, "English", 2)) yield {

      // Extract associated terms
      def refs(header: String) = Helper.headers(english, header).flatMap(Helper.references).map(_.ref)
      val related = Seq(
        "Alternative forms",
        "Abbreviation",
        "Symbol",
        "See also",
        "Synonyms",
        "Antonyms",
        "Hyponyms",
        "Derived terms",
        "Related terms",
        "Anagrams"
      ).flatMap(refs).sorted

      // Extract definitions and examples
      val definitions = "" :: Nil
      // TODO extract definitions and examples sentences

      // Create JSON object
      //println("> " + title)
      Json.obj(
        "word" -> title,
        "synonyms" -> related,
        "definitions" -> definitions
      )
    }

  }

  def main(args: Array[String]) {

    // Local file path
    val path = "../data/enwiktionary-latest-pages-articles.xml.bz2"

    // Get (uncompressed) input stream
    var input: InputStream = new BufferedInputStream(new FileInputStream(path))
    if (path.endsWith(".bz2"))
      input = new CompressorStreamFactory().createCompressorInputStream(input)

    // Iterate and write on disk
    for ((it, i) <- Parallel.split(new Pages(input)).zipWithIndex.par) {
      for ((objs, j) <- it.flatMap(p => extract(p._1, p._2)).grouped(1000).zipWithIndex) {
        Packer.writeBZ2("../data/definitions/wiktionary_" + i + "_" + j + ".json.bz2", Packer.pack(objs))
        println("-> core #" + i + ", chunk #" + j)
      }
      println("=> core #" + i + " finished")
    }

    // Close resources
    input.close()

  }

}
