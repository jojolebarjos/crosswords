package crosswords.data.mine

import java.io._
import java.nio.charset.StandardCharsets
import java.nio.file.{FileSystems, Files}
import java.util.Base64

import play.api.libs.json.Json

import scala.collection._

/**
 * Created by Vincent on 21.03.2015.
 */
object Wiki_to_JSON {

  val wiki_redirect = """\[\[([^\[\]\|]+)\]\]""".r
  val wiki_piped_link = """\[\[([^\[\]\|]+:?[^\[\]\|]+)(\|(.+))\]\]""".r
  val wiki_anchor = """\{\{.*\}\}""".r

  val decoder = Base64.getUrlDecoder

  object InState extends Enumeration {
    type Instate = Value
    val none, prp_noun, noun, verb, adjective,
    adverb, synonym, antonym, relative, derived = Value
  }

  /* TODO: This will have to be parsed more carefully because templates of the form {{foo|bar|baz|...}}
      can contain meaningful information */
  def remove_wikistuff(l: String): String = {
    var line = l
    wiki_anchor.findAllIn(line).matchData.foreach(m => line = line.replaceAllLiterally(m.toString(), ""))
    wiki_piped_link.findAllIn(line).matchData.foreach(m => line = line.replaceAllLiterally(m.toString(), m.group(3)))
    wiki_redirect.findAllIn(line).matchData.foreach(m => line = line.replaceAllLiterally(m.toString(), m.group(1)))
    line = line.replaceAllLiterally("*", "")
    line = line.replaceAllLiterally("#:", "")
    line = line.replaceAllLiterally("#", "")
    line
  }

  def parse(inDir: String, outDir: String): Unit = {
    var in_English = false
    var inType = InState.none
    var containsEnglish = false

    var definitions: mutable.ListBuffer[(String, String)] = null
    var references: mutable.ListBuffer[(String, String)] = null

    // Stream the files in the input directory
    val dirStream = Files.newDirectoryStream(FileSystems.getDefault.getPath(inDir))
    val dirIter = JavaConverters.asScalaIteratorConverter(dirStream.iterator()).asScala
    for (filePath <- dirIter) {
      val file = Files.newBufferedReader(filePath, StandardCharsets.UTF_8)

      // Stream each line in this file
      for (line <- Stream.continually(file.readLine()).takeWhile(_ != null)) {
        line match {
          //cases where we "hop" into a new type
          case "==English==" =>
            in_English = true
            containsEnglish = true
            definitions = mutable.ListBuffer()
            references = mutable.ListBuffer()
          case "----" => in_English = false
          case "===Proper Noun===" => inType = InState.prp_noun
          case "===Noun===" => inType = InState.noun
          case "===Verb===" => inType = InState.verb
          case "===Adjective===" => inType = InState.adjective
          case "===Adverb===" => inType = InState.adverb
          case "====Synonyms====" => inType = InState.synonym
          case "====Antonyms====" => inType = InState.antonym
          case "====Related terms====" => inType = InState.relative
          case "====Derived terms====" => inType = InState.derived
          case l if l.startsWith("====") => inType = InState.none
          //cases where we are in the type and are retrieving information
          case l if in_English && inType != InState.none && l.startsWith("#") =>
            definitions += ((inType.toString, remove_wikistuff(l).trim))
          case l if in_English && inType != InState.none && l.startsWith("*") =>
            references += ((inType.toString, remove_wikistuff(l).trim))
          case _ => //do nothing
        }
      }

      if (containsEnglish) {
        references = references.filter(!_._2.isEmpty)
        definitions = definitions.filter(!_._2.isEmpty)

        val wordEncoded = filePath.getFileName.toString.replace(".txt", "")
        val word = new String(decoder.decode(wordEncoded.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8)

        val json = Json.obj(
          "word" -> word,
          "language" -> "English",
          "definitions" -> Json.toJson(definitions.map {
            case df => Json.obj(
              "type" -> df._1,
              "definition" -> df._2
            )
          }),
          "synonyms" -> Json.toJson(
            references.filter(_._1 == InState.synonym.toString).map(_._2)),
          "antonyms" -> Json.toJson(
            references.filter(_._1 == InState.antonym.toString).map(_._2)),
          "derived terms" -> Json.toJson(
            references.filter(_._1 == InState.derived.toString).map(_._2)),
          "relative terms" -> Json.toJson(
            references.filter(_._1 == InState.relative.toString).map(_._2))
        )

        val fileOut = new FileOutputStream(outDir + wordEncoded + ".json")
        val out = new BufferedWriter(new OutputStreamWriter(fileOut, StandardCharsets.UTF_8))
        out.write(Json.prettyPrint(json))
        out.close()

        in_English = false
        containsEnglish = false
        definitions = null
        references = null
        file.close()
      }
    }
    dirStream.close()
  }

  def main(arg: Array[String]): Unit = {
    //launch teh missiles
    val in_addr = "../data/wiki/wikiMarkup/"
    val out_addr = "../data/wiki/wikiJson/"
    parse(in_addr, out_addr)
  }
}
