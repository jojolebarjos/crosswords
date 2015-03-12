package crosswords.data

import crosswords.util.Direction
import play.api.libs.json._

/**
 * This class represents a word in the context of a crossword grid.
 * @param word
 * @param clue
 * @param x
 * @param y
 * @param direction
 */
case class WordClue(word: String, clue: String, x: Int, y: Int, direction: Direction /* = East? */) {
  // TODO: bound checking, null checking?
}

object WordClue {
  def fromJSON(jsValue: JsValue): Option[WordClue] = {
    val word = (jsValue \ "word").asOpt[String]
    val clue = (jsValue \ "clue").asOpt[String]
    val x = (jsValue \ "x").asOpt[Int]
    val y = (jsValue \ "y").asOpt[Int]
    val direction = (jsValue \ "dir").asOpt[String].flatMap(Direction.fromString)

    if (List(word, clue, x, y, direction).forall(_.isDefined)) {
      Some(WordClue(word.get, clue.get, x.get, y.get, direction.get))
    } else {
      // Log.warn("[WORDCLUE] Failed to create WordClue")
      None
    }
  }
}
