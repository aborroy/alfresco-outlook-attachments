package org.alfresco.behaviour;

import org.alfresco.model.ContentModel;
import org.alfresco.mail.AttachmentsExtractor;
import org.alfresco.repo.node.NodeServicePolicies;
import org.alfresco.repo.policy.Behaviour;
import org.alfresco.repo.policy.JavaBehaviour;
import org.alfresco.repo.policy.PolicyComponent;
import org.alfresco.service.cmr.repository.ChildAssociationRef;
import org.alfresco.service.cmr.repository.ContentService;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.repository.NodeService;
import org.alfresco.service.namespace.NamespaceService;
import org.alfresco.service.namespace.QName;

import javax.activation.MimetypesFileTypeMap;
import javax.mail.Session;
import javax.mail.internet.MimeMessage;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class ExtractOutlookAttachments implements NodeServicePolicies.OnCreateNodePolicy {

    private NodeService nodeService;
    private ContentService contentService;
    private PolicyComponent policyComponent;
    private List<String> mimetypeList;
    private AttachmentsExtractor attachmentsExtractor;

    private Behaviour onCreateNode;

    public void init() {
        this.onCreateNode = new JavaBehaviour(this, "onCreateNode", Behaviour.NotificationFrequency.TRANSACTION_COMMIT);
        this.policyComponent.bindClassBehaviour(
                QName.createQName(NamespaceService.ALFRESCO_URI, "onCreateNode"),
                ContentModel.TYPE_CONTENT,
                this.onCreateNode);
    }

    @Override
    public void onCreateNode(ChildAssociationRef childAssociationRef) {

        NodeRef nodeRef = childAssociationRef.getChildRef();
        if (nodeService.exists(nodeRef)) {
            String mimetype = getMimeTypeFromFileName(nodeRef);
            if (mimetypeList.contains(mimetype)) {
                InputStream is = contentService.getReader(nodeRef, ContentModel.PROP_CONTENT).getContentInputStream();
                try {
                    MimeMessage message = new MimeMessage(Session.getDefaultInstance(new Properties()), is);
                    attachmentsExtractor.extractAttachments(nodeRef, message);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

    }

    public String getMimeTypeFromFileName(NodeRef nodeRef) {
        String nodeName = (String) nodeService.getProperty(nodeRef, ContentModel.PROP_NAME);
        return MimetypesFileTypeMap.getDefaultFileTypeMap().getContentType(nodeName);
    }

    public void setNodeService(NodeService nodeService) {
        this.nodeService = nodeService;
    }

    public void setContentService(ContentService contentService) {
        this.contentService = contentService;
    }

    public void setPolicyComponent(PolicyComponent policyComponent) {
        this.policyComponent = policyComponent;
    }

    public void setMimetypeList(String mimetypeList) {
        this.mimetypeList = Arrays.asList(mimetypeList.split(","));
    }

    public void setAttachmentsExtractor(AttachmentsExtractor attachmentsExtractor) {
        this.attachmentsExtractor = attachmentsExtractor;
    }

}