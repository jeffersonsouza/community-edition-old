/*
 * Copyright (C) 2005-2008 Alfresco Software Limited.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.

 * As a special exception to the terms and conditions of version 2.0 of 
 * the GPL, you may redistribute this Program in connection with Free/Libre 
 * and Open Source Software ("FLOSS") applications as described in Alfresco's 
 * FLOSS exception.  You should have recieved a copy of the text describing 
 * the FLOSS exception, and it is also available here: 
 * http://www.alfresco.com/legal/licensing"
 */
package org.alfresco.web.site.renderer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.alfresco.web.site.RenderData;
import org.alfresco.web.site.RequestContext;
import org.alfresco.web.site.RequestUtil;
import org.alfresco.web.site.exception.RendererExecutionException;

/**
 * @author muzquiano
 */
public class JSPRenderer extends AbstractRenderer
{
    public void execute(RequestContext context, HttpServletRequest request,
            HttpServletResponse response, RenderData renderData)
            throws RendererExecutionException
    {
        String renderer = this.getRenderer();

        // execute
        try
        {
            // put the file URI into the config
            String dispatchPath = renderer;
            renderData.put("jsp-file-uri", dispatchPath);

            // put the folder URI into the config
            int x = dispatchPath.lastIndexOf("/");
            String pathUri = dispatchPath.substring(0, x);
            renderData.put("jsp-path-uri", pathUri);

            // do the include
            RequestUtil.include(request, response, dispatchPath);
        }
        catch (Exception ex)
        {
            throw new RendererExecutionException(ex,
                    "Unable to execute JSP include: " + renderer);
        }
    }
}
