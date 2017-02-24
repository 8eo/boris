name := "boris"
organization := "co.horn"
version := "0.0.8"
scalaVersion := "2.12.1"
crossScalaVersions := Seq("2.11.8", "2.12.1")
fork in Test := true
scalafmtConfig := Some(file(".scalafmt.conf"))

scalacOptions ++= Seq(
  "-unchecked",
  "-deprecation",
  "-Ywarn-dead-code",
  "-encoding", "UTF-8",
  "-target:jvm-1.8",
  "-feature",
  "-language:postfixOps")

libraryDependencies ++= {
  val akkaHttpV    = "10.0.4"
  val scalaTestV   = "3.0.1"
  Seq(
    "com.typesafe.akka"   %% "akka-http"             % akkaHttpV,
    "com.typesafe.akka"   %% "akka-http-spray-json"  % akkaHttpV,
    "com.typesafe.akka"   %% "akka-http-testkit"     % akkaHttpV     % "test",
    "org.scalatest"       %% "scalatest"             % scalaTestV    % "test"
  )
}
