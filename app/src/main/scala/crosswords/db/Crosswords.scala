
package crosswords.db

import Helper._
import play.api.libs.json.JsObject

/**
 * Crossword database tools.
 *
 * @author Utku Sirin
 * @author Johan Berdat
 */
object Crosswords {

  /**
   * Drop DatabaseDB
   */
  def drop() {

    // Open connection, with no specific database
    using(open()) { connection =>

      // Drop the table
      update(connection, "drop database if exists CrosswordDB")
    }

  }

  /**
   * Create DatabaseDB.
   */
  def create() {

    // Open connection, with no specific database
    using(open()) { connection =>

      // Create the table
      update(connection, "create database CrosswordDB")
    }

    // Reopen database, with the new database
    using(open("CrosswordDB")) { connection =>

      // Create crosswords attributes table
      update(connection, """
          |create table CrosswordAttributes (
          |    cwid int,
          |    source varchar(50),
          |    language varchar(20),
          |    title varchar(50),
          |    url varchar(100),
          |    author varchar(30),
          |    cwdate date,
          |    difficulty int,
          |    primary key (cwid)
          |)
        """.stripMargin)

      // Create words table
      update(connection, """
          |create table Words (
          |    wid int,
          |    word varchar(50),
          |    primary key (wid)
          |)
        """.stripMargin)

      // Create crosswords table
      update(connection, """
          |create table Crosswords (
          |    cwid int,
          |    wid int,
          |    xcoord int,
          |    ycoord int,
          |    clue varchar(100),
          |    direction varchar(10),
          |    primary key (cwid,wid),
          |    foreign key (cwid)
          |        references CrosswordAttributes(cwid)
          |        on update cascade on delete cascade,
          |    foreign key (wid)
          |        references Words(wid)
          |        on update cascade on delete cascade
          |)
        """.stripMargin)

      // Create categories table
      update(connection, """
          |create table Categories (
          |    cid int,
          |    category varchar(50),
          |    primary key (cid)
          |)
        """.stripMargin)

      // Create crossword categories table
      update(connection, """
          |create table CrosswordCategories (
          |    cwid int,
          |    cid int,
          |    primary key (cwid,cid),
          |    foreign key (cwid)
          |        references CrosswordAttributes(cwid)
          |        on update cascade on delete cascade,
          |    foreign key (cid)
          |        references Categories(cid)
          |        on update cascade on delete cascade
          |)
        """.stripMargin)
    }

  }

  /**
   * Upload crosswords in JSON format.
   */
  def upload(crosswords: Iterator[JsObject]) {

    // Open database
    using(open("CrosswordDB")) { connection =>

      // TODO create statement and upload crosswords

    }

  }

  // TODO add some download helper

  def main(args: Array[String]) {

    drop()
    create()

  }

}
