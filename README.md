# Alfresco Outlook Attachments Extractor

This is an ACS project for Alfresco SDK 4.4 (ACS 7.2).

When uploading an Outlook Message File (EML, MSG) to Alfresco, this addon extracts the attachment files and stores them in the `SAME` folder or in a `SEPARATE` folder.

Following parameters can be adapted using `alfresco-global.properties` file or Java Environment variables.

```
# List of mimetypes to consider
extract.attachments.mimetype.list=message/rfc822,application/vnd.ms-outlook
# SAME, SEPARATE
imap.attachments.mode=SEPARATE
```

## Building

Build the code as a regular Maven project.

```
$ mvn clean package
$ ls target/
alfresco-outlook-attachments-1.0.0.jar
```

## Deploying

Deploy this addon as a regular JAR library to Alfresco Repository WAR.

```
$ cp alfresco-outlook-attachments-1.0.0.jar $TOMCAT_DIR/webapps/alfresco/WEB-INF/lib
```
