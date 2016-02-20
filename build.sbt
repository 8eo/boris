lazy val boris = project
  .copy(id = "boris")
  .in(file("."))
  .enablePlugins(AutomateHeaderPlugin, GitVersioning)

name := "boris"

libraryDependencies ++= Vector(
  Library.scalaCheck % "test"
)

initialCommands := """|import monadic.boris._
                      |""".stripMargin
