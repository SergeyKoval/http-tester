## Description
Simple HTTP tester which sends POST request to specified URL with **username**
and **passcode** parameters as body.
## Install
This is maven project, so use `mvn:install` to get things done.

## Usage
To use this all, you need to complete next steps:
1. Make installation
2. After installation get `http-tester-1.0-SNAPSHOT.jar` file and `lib` directory and 
put them side by side.
3. Add to `config.properties` file next to previous two.
4. Run all the stuff like this:
`java -jar http-tester-1.0-SNAPSHOT.jar`

## Config
`config.properties` should contain 3 properties:
1. **url** - url to request
2. **username** - username parameter
3. **passcode** - passcode parameter