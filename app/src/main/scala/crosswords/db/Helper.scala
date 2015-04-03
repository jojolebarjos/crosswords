
package crosswords.db

import java.sql.{SQLException, DriverManager, Connection}

/**
 * Helpers for database manipulation.
 *
 * @author Utku Sirin
 * @author Johan Berdat
 */
object Helper {

  val DEFAULT_URL = "jdbc:mysql://localhost:3306/"
  val DEFAULT_USER = "root"
  val DEFAULT_PASSWORD = "Root2015"

  /**
   * Create a new connection using specified url and credentials.
   */
  def open(url: String, user: String, password: String, table: String): Connection =
    DriverManager.getConnection(url + table + "?user=" + user + "&password=" + password)

  /**
   * Create a new connection using default url and credentials.
   */
  def open(table: String = ""): Connection =
    open(DEFAULT_URL, DEFAULT_USER, DEFAULT_PASSWORD, table)

  /**
   * Safely close specified connection.
   */
  def close(connection: Connection) {
    try connection.close() catch { case _: SQLException => /* empty on purpose */ }
  }

  /**
   * Safe block for connection usage.
   * The connection is closed at the end.
   */
  def using[A](connection: Connection)(code: Connection => A): A =
    try code(connection) finally close(connection)

  /**
   * Execute a SQL update.
   */
  def update(connection: Connection, sql: String): Int = {
    val statement = connection.prepareStatement(sql)
    statement.executeUpdate()
  }

  /**
   * Create a result set for specified query.
   * A row and time limit can be specified.
   */
  def query(connection: Connection, sql: String, max: Int = 0, timeout: Int = 0): Result = {
    val statement = connection.createStatement()
    if (max > 0)
      statement.setMaxRows(max)
    if (timeout > 0)
      statement.setQueryTimeout(timeout)
    new Result(statement, sql)
  }

  /**
   * Safe block for result usage.
   * The result is closed at the end.
   */
  def using[A](result: Result)(code: Result => A): A =
    try code(result) finally result.close()

  /**
   * Query the max value of some integer field in specified table.
   * Return -1 if the table is empty.
   */
  def max(connection: Connection, table: String, field: String): Int = {
    val sql = "select coalesce(max(" + field + "), -1) from " + table
    using(query(connection, sql))(_.as1[Int].next())
  }

}
