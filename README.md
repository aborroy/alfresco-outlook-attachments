# Alfresco Outlook Attachments Extractor

This is an ACS project for ACS 26.2 (Java 21).

## Compatibility

| Alfresco (ACS) | Java | Mail API | Branch / tag |
|---|---|---|---|
| 26.2 | 21+ | `jakarta.mail` | `main` |
| 7.2 | 11+ | `javax.mail` | tag `v1.0.0-acs7` |

The move to ACS 26.2 replaces `javax.mail` / `javax.activation` with their Jakarta counterparts, so the latest code does not run on older Alfresco or Java versions. Use the legacy tag for those.

When uploading an Outlook Message File (EML, MSG) to Alfresco, this addon extracts the attachment files and stores them in the `SAME` folder or in a `SEPARATE` folder.

Following parameters can be adapted using `alfresco-global.properties` file or Java Environment variables.

```
# List of mimetypes to consider
extract.attachments.mimetype.list=message/rfc822,application/vnd.ms-outlook
# SAME, SEPARATE
imap.attachments.mode=SEPARATE
# Folder's suffix in SEPARATE mode
# Fallback to -attachments if null or couldn't be read
imap.attachments.folder.suffix=-attachment
```

## Building

Build the code as a regular Maven project.

```
$ mvn clean package
$ ls target/
alfresco-outlook-attachments-2.0.0.jar
```

## Deploying

Deploy this addon as a regular JAR library to Alfresco Repository WAR.

```
$ cp alfresco-outlook-attachments-2.0.0.jar $TOMCAT_DIR/webapps/alfresco/WEB-INF/lib
```
