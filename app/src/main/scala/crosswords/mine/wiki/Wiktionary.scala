
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

  private def clean(title: String) =
    title.toLowerCase.filter(c => c >= 'a' && c <= 'z').trim

  /**
   * Abbreviations, alternate forms, alternate spelling, symbols...
   */
  def isEquivalent(title: String): Boolean = {
    val t = clean(title)
    if (t.contains("abbreviation"))
      return true
    if (t.contains("alternat") && (t.contains("form") || t.contains("spell")))
      return true
    if (t.contains("symbol"))
      return true
    false
  }

  /**
   * Hyperonyms, hyponyms, holonyms, synonyms, antonyms,
   * related terms/forms, derived terms, see also,
   * homographs, homonyms, homophones, anagrams...
   */
  def isAssociated(title: String): Boolean = {
    val t = clean(title)
    if (t.contains("ym") && (t.contains("hyper") || t.contains("hypo") || t.contains("holo") || t.contains("sy") || t.contains("anto")))
      return true
    if (t.contains("rel") && (t.contains("term") || t.contains("form") || t.contains("char")))
      return true
    if (t.contains("der") && t.contains("ed"))
      return true
    if (t == "seealso")
      return true
    if (t.contains("homo"))
      return true
    if (t.contains("anagram"))
      return true
    false
  }

  def extract(title: String, page: String): Seq[JsObject] = {

    // Get english section
    val markup = Markup(page)
    for (english <- Helper.headers(markup, "English", 2)) yield {

      // Clean undesired symbols and tags
      def clean(ref: Reference) = {
        var result = ref.ref
        val colon = result.lastIndexOf(':')
        if (colon >= 0)
          result = result.substring(colon + 1)
        val hash = result.indexOf('#')
        if (hash >= 0)
          result = result.substring(0, hash)
        result
      }

      // Enumerate and clean references
      def refs(pred: String => Boolean) =
        Helper.headers(english, h => pred(h.title.toString)).flatMap(Helper.references).map(clean).distinct.sorted
      val equivalents = refs(isEquivalent)
      val associated = refs(isAssociated)

      // Extract definitions and examples
      val definitions = Seq.empty[String]
      val examples = Seq.empty[String]
      // TODO extract definitions and examples sentences

      // Create JSON object
      //println("> " + title)
      var obj =  Json.obj("word" -> title)
      if (equivalents.nonEmpty)
        obj += "equivalents" -> Json.toJson(equivalents)
      if (associated.nonEmpty)
        obj += "associated" -> Json.toJson(associated)
      if (definitions.nonEmpty)
        obj += "definitions" -> Json.toJson(definitions)
      if (examples.nonEmpty)
        obj += "examples" -> Json.toJson(examples)
      obj

    }

  }

  def main(args: Array[String]) {

    // Local file path
    val path = "../data/enwiktionary-latest-pages-articles.xml.bz2"
    val count = 576160 // all pages: 3996006

    // Get (uncompressed) input stream
    var input: InputStream = new BufferedInputStream(new FileInputStream(path))
    if (path.endsWith(".bz2"))
      input = new CompressorStreamFactory().createCompressorInputStream(input)

    // Iterate and write on disk
    var current = 0
    for ((it, i) <- Parallel.split(new Pages(input)).zipWithIndex.par) {
      for ((objs, j) <- it.flatMap(p => extract(p._1, p._2)).grouped(1000).zipWithIndex) {
        Packer.writeBZ2("../data/definitions/wiktionary_" + i + "_" + j + ".json.bz2", Packer.pack(objs))
        val c = synchronized{current += 1000; current}
        println("-> core #" + i + ", chunk #" + j + ", " + (100.0f * c / count) + "%")
      }
      println("=> core #" + i + " finished")
    }

    // Close resources
    input.close()

  }

}
