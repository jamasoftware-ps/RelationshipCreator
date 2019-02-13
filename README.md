# Jama Software
Jama Software is the definitive system of record and action for product development. The company’s modern requirements and test management solution helps enterprises accelerate development time, mitigate risk, slash complexity and verify regulatory compliance. More than 600 product-centric organizations, including NASA, Boeing and Caterpillar use Jama to modernize their process for bringing complex products to market. The venture-backed company is headquartered in Portland, Oregon. For more information, visit [jamasoftware.com](http://jamasoftware.com).

Please visit [dev.jamasoftware.com](http://dev.jamasoftware.com) for additional resources and join the discussion in our community [community.jamasoftware.com](http://community.jamasoftware.com).

## Relationship Creator
This application leverages the Jama REST API to import relationships from a .csv file.  
- Items to be related are identified by any value in any text field.  
- The type of relationship to create is specifiable.  
- A delay between API requests can be imposed to avoid impacting system performance.

Please note that this application is distrubuted as-is as an example and will likely require modification to work for your specific use-case. It's been developer tested with Java 7 and 8. Jama Support will not assist with the use or modification of the application.

### Build
The dependencies are listed at the bottom of this document.  To build with Maven:
```mvn clean compile assembly:single```

### Before you begin
- Ensure that you have Java 7 or 8 installed on the machine where the application will run. 
- Your Jama instance must have access to the REST API. 
- The source of relationships for this application is a .csv file with format detailed below. 
- Always test API code in a development environment before moving it to production.

### Create relationships using the RelationshipCreator application
1. Execute the JAR file to create the configuration document: 
- Place the RelationshipsCreator.jar file in a directory.  
- Execute the JAR file by running it from the command line with “java -jar RelationshipImporter.jar” (or you can double click the file).  A file, import.cfg, is created in the directory. 
2. Enter options in the configuration document: 
- See the options for the configuration document below. 
3. Save the configuration document.  The name must be “import.cfg”. 
4. Execute the jar file from the command line with “java -jar RelationshipCreator.jar”. Progress updates as the application creates the relationships.  A log file is generated and updated as the application works. 
- The logfile is named “importLog<time stamp>.log” 
- Double click the JAR file to hide progress.

### Now that you’re done
- The relationships described in the .csv file now exist in your Jama instance. 
- A log file was created detailing the actions of the importer. 
- You can rollback any changes that the importer made (see “Rollback” below).

### Config file options
Change these options as required (in step 2 above). 

```delimiter = <single character> ``` -- A single character, usually a comma or semicolon.  This character is what separates elements of the CSV file.  It’s also used in the lists in the rest of this file. 

-- CHOOSE 1 Authentication method, fill in the appropriate settings, Leave the other set empty, but do not delete the option entry--
-- To Use Basic Auth Enter your Username and Password, Leave ClientID and ClientSecret set to the empty string. --
-- To Enable OAuth Enter your ClientID an ClientSecret, Leave Username and Password set to the empty string.--

```username = <string> ``` -- Jama username. 

```password = <string> ``` -- Jama password. 

```clientID = <string>``` -- Jama Oauth Client ID

```clientSecret = <string>``` -- Jama Oauth Client Secret

```baseURL = <string> ``` -- The base URL of the Jama instance. For hosted this is {base url}/rest/ For on-prem it’s {base url}/contour/rest/

```csvFile = <string> ``` -- The name of a CSV file in the same directory as the JAR file. 

```columnAItemTypeAPI-ID = <integer> ``` -- The API ID of the item type for the first column.  This can be found in the admin area. 

```columnAFieldName = <string> ``` -- The field name of the values in the first column.  This is not the label.  Examples are: “documentKey” and “name” 

```columnAProjects = <list of integers> ``` -- A list of one or more projects to search while trying to create relationships for the first column.  This list is composed of project API IDs and is separated by the character in the delimiter option.  Project API IDs can be found in the admin area or under configure project. 

```columnBItemTypeAPI-ID = <integer> ``` -- The API ID of the item type for the second column.  This can be found in the admin area. 

```columnBFieldName = <string> ``` -- The field name of the values in the second column.  This is not the label.  Examples are: “documentKey” and “name” 

```columnBProjects = <list of integers> ``` -- A list of one or more projects to search while trying to create relationships for the second column.  This list is composed of project API IDs and is separated by the character in the delimiter option.  Project API IDs can be found in the admin area or under configure project. 

```delay = <integer> ``` -- A number of milliseconds to wait between API requests.  A value of 0 here will cause the application to make relationships as often as possible. 

```retries = <integer> ``` -- The number of times to attempt to create a relationship if an attempt is unsuccessful.  A first-time failure may be due to network conditions, the Jama application being busy, etc.


### CSV file
- Composed of multiple rows separated by newlines. 
- Each row is composed of 2 or 3 fields, separated by whichever character was specified in the delimiter option. 
- The third column is used to specify what type of relationship to create.  The relationship name is not case sensitive.  If nothing is specified, the default relationship will be created. If the value specified is not found a log entry will be created and the default relationship type will be used.

### Rollback
- To delete relationships created by the application, change the name of the log file generated while the relationships were being created to rollback.log and run the application. 
- If a file called rollback.log exists in the same directory as the JAR file, the application will attempt to delete relationships. 
- A line in rollback.log that begins with “NEW:” will generate an attempt to delete a relationship.  For example, the line “NEW: 123” will attempt to delete the relationship with API ID 123.

### Notes
- If the application is run by double-clicking the JAR file (or any other way that doesn’t involve the command line) there is no indication of progress.  Activity is still logged and you can tell when the application is finished because the file “importLog<timestamp>.log.lck” will be deleted. 
- Rich text fields often come with html tags that are hard to account for.  This application was designed for use with plain text fields only. 
- If the application tries to create a relationship which the user isn’t authorized to create, it will stop executing.
- If the “retries” option is set to a value other than 1, only the final attempt will generate errors in the log. 
- If there is an unhandled error or other system crash, it’s a good idea to check the Jama activity stream.  The log is updated frequently, but there may be one or two relationships which are created but not logged if the application doesn’t exit normally. 
- If the application’s execution ends before all relationships in the csv are created it can just be run again.  If a relationship already exists between two items a log entry is generated but no other action is taken.  To speed up execution after a crash it may be helpful to delete from the CSV file some or all of the relationships that were created.  They don’t do any harm but it takes time to try to create them. 
- The following dependencies are used in this application:
https://code.google.com/p/json-simple/
https://hc.apache.org/httpcomponents-client-ga/
