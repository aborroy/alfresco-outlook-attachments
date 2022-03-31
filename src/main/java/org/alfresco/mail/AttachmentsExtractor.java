package org.alfresco.mail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.List;

import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.ContentType;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeUtility;

import org.alfresco.model.ContentModel;
import org.alfresco.model.ImapModel;
import org.alfresco.service.ServiceRegistry;
import org.alfresco.service.cmr.model.FileFolderService;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.MimetypeService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.poi.hmef.HMEFMessage;
import org.springframework.util.FileCopyUtils;

public class AttachmentsExtractor
{
    public enum AttachmentsExtractorMode
    {
        SAME,       // Attachments will be extracted to the same folder where email lies.
        SEPARATE    // All attachments for each email will be extracted to separate folder.
    }

    private Log logger = LogFactory.getLog(AttachmentsExtractor.class);

    private FileFolderService fileFolderService;
    private NodeService nodeService;
    private ServiceRegistry serviceRegistry;
    private AttachmentsExtractorMode attachmentsExtractorMode;
    private MimetypeService mimetypeService;

    public void extractAttachments(NodeRef messageRef, MimeMessage originalMessage) throws IOException, MessagingException
    {
        NodeRef attachmentsFolderRef = null;
        String attachmentsFolderName = null;
        boolean createFolder = false;
        switch (attachmentsExtractorMode)
        {
            case SAME:
                attachmentsFolderRef = nodeService.getPrimaryParent(messageRef).getParentRef();
                break;
            case SEPARATE:
            default:
                String messageName = (String) nodeService.getProperty(messageRef, ContentModel.PROP_NAME);
                attachmentsFolderName = messageName + "-attachments";
                createFolder = true;
                break;
        }
        if (!createFolder)
        {
            nodeService.createAssociation(messageRef, attachmentsFolderRef, ImapModel.ASSOC_IMAP_ATTACHMENTS_FOLDER);
        }

        Object content = originalMessage.getContent();
        if (content instanceof Multipart)
        {
            Multipart multipart = (Multipart) content;

            for (int i = 0, n = multipart.getCount(); i < n; i++)
            {
                Part part = multipart.getBodyPart(i);
                if ("attachment".equalsIgnoreCase(part.getDisposition()))
                {
                    if (createFolder)
                    {
                        attachmentsFolderRef = createAttachmentFolder(messageRef, attachmentsFolderName);
                        createFolder = false;
                    }
                    createAttachment(messageRef, attachmentsFolderRef, part);
                }
            }
        }

    }

    private NodeRef createAttachmentFolder(NodeRef messageRef, String attachmentsFolderName)
    {
        NodeRef attachmentsFolderRef = null;
        NodeRef parentFolder = nodeService.getPrimaryParent(messageRef).getParentRef();
        attachmentsFolderRef = fileFolderService.create(parentFolder, attachmentsFolderName, ContentModel.TYPE_FOLDER).getNodeRef();
        nodeService.createAssociation(messageRef, attachmentsFolderRef, ImapModel.ASSOC_IMAP_ATTACHMENTS_FOLDER);
        return attachmentsFolderRef;
    }

    private void createAttachment(NodeRef messageFile, NodeRef attachmentsFolderRef, Part part) throws MessagingException, IOException
    {
        String fileName = part.getFileName();
        if (fileName == null || fileName.isEmpty())
        {
            fileName = "unnamed";
        }
        try
        {
            fileName = MimeUtility.decodeText(fileName);
        }
        catch (UnsupportedEncodingException e)
        {
            if (logger.isWarnEnabled())
            {
                logger.warn("Cannot decode file name '" + fileName + "'", e);
            }
        }

        ContentType contentType = new ContentType(part.getContentType());

        if (contentType.getBaseType().equalsIgnoreCase("application/ms-tnef"))
        {
            // The content is TNEF
            HMEFMessage hmef = new HMEFMessage(part.getInputStream());

            // hmef.getBody();
            List<org.apache.poi.hmef.Attachment> attachments = hmef.getAttachments();
            for (org.apache.poi.hmef.Attachment attachment : attachments)
            {
                String subName = attachment.getLongFilename();

                NodeRef attachmentNode = fileFolderService.searchSimple(attachmentsFolderRef, subName);
                if (attachmentNode == null)
                {
                    /*
                     * If the node with the given name does not already exist Create the content node to contain the attachment
                     */
                    FileInfo createdFile = fileFolderService.create(attachmentsFolderRef, subName, ContentModel.TYPE_CONTENT);

                    attachmentNode = createdFile.getNodeRef();

                    serviceRegistry.getNodeService().createAssociation(messageFile, attachmentNode, ImapModel.ASSOC_IMAP_ATTACHMENT);

                    byte[] bytes = attachment.getContents();
                    ContentWriter writer = fileFolderService.getWriter(attachmentNode);

                    String extension = attachment.getExtension();
                    String mimetype = mimetypeService.getMimetype(extension);
                    if (mimetype != null)
                    {
                        writer.setMimetype(mimetype);
                    }

                    OutputStream os = writer.getContentOutputStream();
                    ByteArrayInputStream is = new ByteArrayInputStream(bytes);
                    FileCopyUtils.copy(is, os);
                }
            }
        }
        else
        {
            NodeRef attachmentFile = fileFolderService.searchSimple(attachmentsFolderRef, fileName);
            if (attachmentFile == null)
            {
                FileInfo createdFile = fileFolderService.create(attachmentsFolderRef, fileName, ContentModel.TYPE_CONTENT);
                nodeService.createAssociation(messageFile, createdFile.getNodeRef(), ImapModel.ASSOC_IMAP_ATTACHMENT);
                attachmentFile = createdFile.getNodeRef();
            }
            else
            {
                String newFileName = generateUniqueFilename(attachmentsFolderRef, fileName);

                FileInfo createdFile = fileFolderService.create(attachmentsFolderRef, newFileName, ContentModel.TYPE_CONTENT);
                nodeService.createAssociation(messageFile, createdFile.getNodeRef(), ImapModel.ASSOC_IMAP_ATTACHMENT);
                attachmentFile = createdFile.getNodeRef();
            }

            nodeService.setProperty(attachmentFile, ContentModel.PROP_DESCRIPTION, nodeService.getProperty(messageFile, ContentModel.PROP_NAME));

            ContentWriter writer = fileFolderService.getWriter(attachmentFile);
            writer.setMimetype(contentType.getBaseType());
            OutputStream os = writer.getContentOutputStream();
            FileCopyUtils.copy(part.getInputStream(), os);
        }
    }

    public String generateUniqueFilename(NodeRef destFolderNodeRef, String fileName)
    {
        if(fileFolderService.searchSimple(destFolderNodeRef, fileName) != null)
        {
            String name = fileName;
            String ext = "";
            if (fileName.lastIndexOf(".") != -1)
            {
                int index = fileName.lastIndexOf(".");
                name = fileName.substring(0, index);
                ext = fileName.substring(index);
            }
            int copyNum = 0;
            do
            {
                copyNum++;
            } while (fileFolderService.searchSimple(destFolderNodeRef, name + " (" + copyNum + ")" + ext) != null);
            fileName =  name + " (" + copyNum + ")" + ext;
        }

        return fileName;
    }

    public void setFileFolderService(FileFolderService fileFolderService)
    {
        this.fileFolderService = fileFolderService;
    }

    public void setNodeService(NodeService nodeService)
    {
        this.nodeService = nodeService;
    }

    public void setServiceRegistry(ServiceRegistry serviceRegistry)
    {
        this.serviceRegistry = serviceRegistry;
    }

    public void setAttachmentsExtractorMode(String attachmentsExtractorMode)
    {
        this.attachmentsExtractorMode = AttachmentsExtractorMode.valueOf(attachmentsExtractorMode);
    }

    public void setMimetypeService(MimetypeService mimetypeService)
    {
        this.mimetypeService = mimetypeService;
    }

}