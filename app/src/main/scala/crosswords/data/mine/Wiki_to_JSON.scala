package crosswords.data.mine

import java.io._
import java.nio.charset.StandardCharsets
import java.util.Base64
import play.api.libs.json.Json
import scala.io.Source

/**
 * Created by Vincent on 21.03.2015.
 */
object Wiki_to_JSON {

  val wiki_redirect = """\[\[([^\[\]\|]+)\]\]""".r
  val wiki_piped_link = """\[\[([^\[\]\|]+:?[^\[\]\|]+)(\|(.+))\]\]""".r
  val wiki_anchor = """\{\{.*\}\}""".r

  object InState extends Enumeration {
    type Instate = Value
    val none, prp_noun, noun, verb, adjective,
    adverb, synonym, antonym, rel_term, der_term = Value
  }



  def remove_wikistuff(l : String) : String = {
    var line = l
    wiki_anchor.findAllIn(line).matchData.foreach(m => line = line.replaceAllLiterally(m.toString, ""))
    wiki_piped_link.findAllIn(line).matchData.foreach(m =>line = line.replaceAllLiterally(m.toString, m.group(3)))
    wiki_redirect.findAllIn(line).matchData.foreach(m => line = line.replaceAllLiterally(m.toString, m.group(1)))
    line =  line.replaceAllLiterally("*", "")
    line = line.replaceAllLiterally("#:", "")
    line = line.replaceAllLiterally("#", "")
    line
  }

  def write(inType : InState.Value, l : String) : List[(String, String)] ={
    val line = remove_wikistuff(l)
      inType match {
        case InState.none => List()
        case _ => List((inType.toString, line))
      }
  }




  def toJson(inDir:String, outDir:String)={


    for(file <- new File(inDir).listFiles.toIterator)
      if (file.isFile && Source.fromFile(file).getLines.contains("==English=="))
      {
        //prepare json for the file
        val fileStream = new FileInputStream(file)
        val reader = new InputStreamReader(fileStream, StandardCharsets.UTF_8)
        val src = new BufferedReader(reader)

        var definitions : List[(String, String)] = List()
        var references : List[(String, String)] = List()

        var inType = InState.none
        for(line <- Source.fromFile(file).getLines) {
          line match {
            //cases where we "hop" into a new type
            case "===Proper Noun===" => inType = InState.prp_noun
            case "===Noun===" => inType = InState.noun
            case "===Verb===" => inType = InState.verb
            case "===Adjective===" => inType = InState.adjective
            case "===Adverb===" => inType = InState.adverb
            case "====Synonyms====" => inType = InState.synonym
            case "====Antonyms====" => inType = InState.antonym
            case "====Related terms====" => inType = InState.rel_term
            case "====Derived terms====" => inType = InState.der_term
            //cases where we are in the type and are retrieving information
            case l if l.startsWith("#") => definitions = definitions ::: write(inType, l)
            case l if l.startsWith("*") => references = references ::: write(inType, l)
            case _ => //do nothing
          }
        }

        var  word = file.getName.replace(".txt","")
        try{
          word = new String(Base64.getUrlDecoder.decode(file.getName.replace(".txt","").trim))
        }catch{case e:IllegalArgumentException =>
          println("could not parse word:" + word)
            println(e)
        }



        val json = Json.obj(
          "word" -> word,
          "language" -> "English",
          "definitions"-> Json.toJson( definitions.map{
            case df => Json.obj(
                "type" -> df._1,
                "definition" -> df._2
            )}),
          "references" ->  Json.toJson( references.map{
            case rf => Json.obj(
                "type" -> rf._1,
                "reference" -> rf._2
              )}))

        val out = new FileWriter(outDir + "/" + file.getName.replace(".txt",".json")  )
        out.write(Json.prettyPrint(json))
        out.close()
        Source.fromFile(file).close

      }
  }

  def main(arg: Array[String]): Unit = {
    //launch teh missiles
    val in_addr = """C:/Users/Vincent/EPFL/Big Data/xml_out_test/"""
    val out_addr = """C:/Users/Vincent/EPFL/Big Data/json_out/"""
    toJson(in_addr, out_addr)
  }
}