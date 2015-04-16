import sbt._
import Keys._

object Build extends Build
{
  // Declare a single project, adding several new commands, which are discussed below.
  lazy override val projects = Seq(app)
  lazy val app = Project("app", file(".")) settings(
    commands += cluster
  )

  val cluster = Command.command("cluster") { state =>
    val extracted = Project.extract(state)
    val dependencies = extracted.get(libraryDependencies)
    val newDependencies = dependencies.map(module => if (module.name.contains("spark")) module % "provided" else module)
    extracted.append(Seq(libraryDependencies := newDependencies), state)
  }
}