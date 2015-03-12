package crosswords.data

import crosswords.util.{South, East}
import play.api.libs.json._

import scala.collection._

import com.github.nscala_time.time.Imports._
import org.joda.time.format.ISODateTimeFormat

/**
 * This class represents a crossword grid.
 * @param words
 * @param args
 */
case class Grid(words: immutable.Seq[WordClue], args: immutable.Map[String, JsValue]) {

  def title: Option[String] = args.get("title").flatMap(_.asOpt[String])
  def url: Option[String] = args.get("url").flatMap(_.asOpt[String])
  def source: Option[String] = args.get("source").flatMap(_.asOpt[String])
  def categories: Option[immutable.Seq[String]] = args.get("categories").flatMap(_.asOpt[Vector[String]])
  def author: Option[String] = args.get("author").flatMap(_.asOpt[String])
  def difficulty: Option[Int] = args.get("difficulty").flatMap(_.asOpt[Int])
  def date: Option[DateTime] = args.get("date").flatMap(_.asOpt[String]).flatMap(s => try{Option(DateTime.parse(s, ISODateTimeFormat.yearMonthDay))} catch {case e:IllegalArgumentException => None})
  def language: Option[String] = args.get("language").flatMap(_.asOpt[String])

  def render(): Unit = {
    def width: Int = {
      val maxWord = words.filter(_.direction == East).maxBy(w => w.x + w.word.length)
      maxWord.x + maxWord.word.length
    }
    def height: Int = {
      val maxWord = words.filter(_.direction == South).maxBy(w => w.y + w.word.length)
      maxWord.y + maxWord.word.length
    }
    def place(view: Array[Array[Char]], wordClue: WordClue): Unit = {
      var x = wordClue.x
      var y = wordClue.y
      for (c <- wordClue.word) {
        view(y)(x) = c
        wordClue.direction match {
          case East => x += 1
          case South => y += 1
        }
      }
    }

    val view = Array.ofDim[Char](height, width)
    for (wc <- words) {
      place(view, wc)
    }

    for (row <- view) {
      for (c <- row) {
        if (c == '\u0000')
          print("  ")
          //print('\u25A0' + " ")
        else
          print(c + " ")
      }
      println()
    }
  }
}

object Grid {
  def fromJSON(input: String): Option[Grid] = {
    val grid = Json.parse(input)
    val argsOpt = grid.asOpt[immutable.Map[String, JsValue]]
    argsOpt match {
      // Search for the arguments
      case Some(args) =>
        // Search specifically for the array of words
        args.get("words").flatMap(_.asOpt[JsArray]) match {
          case Some(array) =>
            // Build each WordClue in the array
            val wordsOpt = array.value.map(WordClue.fromJSON)
            if (wordsOpt.forall(_.isDefined)) {
              val words = wordsOpt.map(_.get).toVector
              Option(Grid(words, args - "words"))
            } else {
              // Log.warn("[GRID] Failed to create each WordClue")
              None
            }
          case None =>
            // Log.warn("[GRID] Could not find words array")
            None
        }
      case None =>
        // Log.warn("[GRID] Could not find grid field")
        None
    }
  }
}