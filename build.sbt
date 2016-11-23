name := "boris"
organization := "co.horn"
version := "0.0.2"
scalaVersion := "2.11.8"
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
  val akkaV        = "2.4.13"
  val akkaHttpV    = "2.4.11"
  val scalaTestV   = "2.2.6"
  Seq(
    "com.typesafe.akka"   %% "akka-actor"                         % akkaV,
    "com.typesafe.akka"   %% "akka-stream"                        % akkaV,
    "com.typesafe.akka"   %% "akka-http-core"                     % akkaHttpV,
    "com.typesafe.akka"   %% "akka-http-experimental"             % akkaHttpV,
    "com.typesafe.akka"   %% "akka-http-spray-json-experimental"  % akkaHttpV,
    "com.typesafe.akka"   %% "akka-http-testkit"                  % akkaHttpV     % "test",
    "org.scalatest"       %% "scalatest"                          % scalaTestV    % "test"
  )
}
