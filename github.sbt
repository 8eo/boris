resolvers += "GitHub Packages" at "https://maven.pkg.github.com/8eo/_"

credentials += Credentials(
  "GitHub Package Registry",
  "maven.pkg.github.com",
  "8eo",
  sys.env("GITHUB_TOKEN")
)
