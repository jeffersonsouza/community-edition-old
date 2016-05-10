package org.alfresco.service.cmr.activities;

import org.alfresco.error.AlfrescoRuntimeException;
import org.alfresco.repo.Client;
import org.alfresco.repo.tenant.TenantService;
import org.alfresco.repo.tenant.TenantUtil;
import org.alfresco.service.cmr.model.FileInfo;
import org.alfresco.service.cmr.repository.NodeRef;
import org.alfresco.service.cmr.site.SiteInfo;
import org.alfresco.service.cmr.site.SiteService;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A consolidated services for posting file folder activities.
 * Some code was moved from webdav.ActivityPosterImpl and
 * opencmis.ActivityPosterImpl.
 *
 * @author Gethin James
 */
public class FileFolderActivityPosterImpl implements ActivityPoster
{
    private static final Logger logger = LoggerFactory.getLogger(FileFolderActivityPosterImpl.class);
    private ActivityService activityService;
    private SiteService siteService;
 
    @Override
    public void postFileFolderActivity(
                String activityType,
                String path,
                String tenantDomain,
                String siteId,
                NodeRef parentNodeRef,
                NodeRef nodeRef,
                String fileName,
                String appTool,
                Client client,
                FileInfo fileInfo)
    {

        JSONObject json;
        try
        {
            json = createActivityJSON(tenantDomain, path, parentNodeRef, nodeRef, fileName);
        }
        catch (JSONException jsonError)
        {
            throw new AlfrescoRuntimeException("Unabled to create activities json", jsonError);
        }
        
        activityService.postActivity(
                    activityType,
                    siteId,
                    appTool,
                    json.toString(),
                    client,
                    fileInfo);
    }

    @Override
    public void postSiteAwareFileFolderActivity(String activityType,
                                                String path,
                                                String tenantDomain,
                                                String siteId,
                                                NodeRef parentNodeRef,
                                                NodeRef nodeRef,
                                                String fileName,
                                                String appTool,
                                                Client client,
                                                FileInfo fileInfo)
    {

        if(siteId == null || siteId.isEmpty())
        {
            SiteInfo siteInfo = siteService.getSite(nodeRef);
            if (siteInfo != null)
            {
                siteId = siteInfo.getShortName();
            }
            else
            {
                //Not a site noderef so return without posting
                if (logger.isDebugEnabled())
                {
                    logger.debug("Non-site activity, so ignored " + activityType + " " + nodeRef);
                }
                return;
            }

        }
        postFileFolderActivity(activityType, path, tenantDomain, siteId, parentNodeRef, nodeRef,
                                fileName, appTool, client, fileInfo);
    }

    /**
     * Create JSON suitable for create, modify or delete activity posts.
     * 
     * @param tenantDomain String
     * @param path String
     * @param parentNodeRef NodeRef
     * @param nodeRef NodeRef
     * @param fileName String
     * @throws JSONException
     * @return JSONObject
     */
    protected JSONObject createActivityJSON(
                String tenantDomain,
                String path,
                NodeRef parentNodeRef,
                NodeRef nodeRef,
                String fileName) throws JSONException
    {
            JSONObject json = new JSONObject();

            json.put("nodeRef", nodeRef);
            
            if (parentNodeRef != null)
            {
                // Used for deleted files.
                json.put("parentNodeRef", parentNodeRef);
            }
            
            if (path != null)
            {
                // Used for deleted files and folders (added or deleted)
                json.put("page", "documentlibrary?path=" + path);
            }
            else
            {
                // Used for added or modified files.
                json.put("page", "document-details?nodeRef=" + nodeRef);
            }
            json.put("title", fileName);
            
            if (tenantDomain!= null && !tenantDomain.equals(TenantService.DEFAULT_DOMAIN))
            {
                // Only used in multi-tenant setups.
                json.put("tenantDomain", tenantDomain);
            }
        
        return json;
    }

    public void setSiteService(SiteService siteService)
    {
        this.siteService = siteService;
    }

    public void setActivityService(ActivityService activityService)
    {
        this.activityService = activityService;
    }
}
