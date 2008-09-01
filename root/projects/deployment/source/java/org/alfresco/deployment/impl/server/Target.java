/*
 * Copyright (C) 2005-2007 Alfresco Software Limited.
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
 * http://www.alfresco.com/legal/licensing
 */

package org.alfresco.deployment.impl.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;

import org.alfresco.deployment.FSDeploymentRunnable;
import org.alfresco.deployment.FileDescriptor;
import org.alfresco.deployment.FileType;
import org.alfresco.deployment.impl.DeploymentException;
import org.alfresco.deployment.util.Path;
import org.alfresco.util.Deleter;
import org.alfresco.util.GUID;

/**
 * This represents a target for a deployment.
 * @author britt
 */
public class Target implements Serializable
{
    private static final long serialVersionUID = 7759718377782991626L;
    private static final String MD_NAME = ".md.";
    private static final String CLONE = "clone";
    private static final String OLD = "old";

    /**
     * The name of the target.
     */
    private String fTargetName;

    /**
     * The root directory of the target deployment.
     */
    private String fRootDirectory;

    /**
     * Where metadata is kept for this target.
     */
    private String fMetaDataDirectory;

    /**
     * The user name for authenticating to this target.
     */
    private String fUser;

    /**
     * The password for authenticating to this target.
     */
    private String fPassword;

    /**
     * Runnables that will be invoked after commit.
     */
    private List<FSDeploymentRunnable> fRunnables;

    /**
     * Make one up.
     * @param name
     * @param root
     * @param metadata
     */
    public Target(String name,
                  String root,
                  String metadata,
                  List<FSDeploymentRunnable> runnables,
                  String user,
                  String password)
    {
        fTargetName = name;
        fRootDirectory = root;
        fMetaDataDirectory = metadata;
        fRunnables = runnables;
        fUser = user;
        fPassword = password;
        // On initial server bringup, there will be no metadata directory.
        // If so then we churn through the current contents and generate
        // initial metadata.
        File meta = new File(fMetaDataDirectory);
        if (meta.exists())
        {
            return;
        }
        initialize();
    }

    /**
     * Helper to initialize metadata for a new deployment Target.
     */
    private void initialize()
    {
        File metadata = new File(fMetaDataDirectory);
        metadata.mkdir();
        File root = new File(fRootDirectory);
        root.mkdir();
        recursiveInitialize(fMetaDataDirectory, fRootDirectory);
    }

    /**
     * Recursively set up fresh metadata.
     * @param metaDir
     * @param dataDir
     */
    private void recursiveInitialize(String metaDir, String dataDir)
    {
        DirectoryMetaData md = new DirectoryMetaData();
        File dFile = new File(dataDir);
        File[] listing = dFile.listFiles();
        for (File child : listing)
        {
            FileDescriptor desc = new FileDescriptor(child.getName(),
                                                     child.isDirectory() ? FileType.DIR : FileType.FILE,
                                                     GUID.generate());
            md.add(desc);
        }
        putDirectory(metaDir + File.separatorChar + MD_NAME, md);
        for (File child : listing)
        {
            if (child.isDirectory())
            {
                String newMetaDir = metaDir + File.separatorChar + child.getName();
                File nmDir = new File(newMetaDir);
                nmDir.mkdir();
                recursiveInitialize(newMetaDir, dataDir + File.separatorChar + child.getName());
            }
        }
    }

    /**
     * Get the target name.
     * @return
     */
    public String getName()
    {
        return fTargetName;
    }

    /**
     * Get the root directory.
     * @return
     */
    public String getRootDirectory()
    {
        return fRootDirectory;
    }

    /**
     * Get the meta data directory.
     * @return
     */
    public String getMetaDataDirectory()
    {
        return fMetaDataDirectory;
    }

    /**
     * Get the username for this target.
     * @return
     */
    public String getUser()
    {
        return fUser;
    }

    /**
     * Get the password for this target.
     * @return
     */
    public String getPassword()
    {
        return fPassword;
    }

    /**
     * Get a File object for the given path in this target.
     * @param path
     * @return
     */
    public File getFileForPath(String path)
   {
        return new File(fRootDirectory + normalizePath(path));
    }
    private static final String fgSeparatorReplacement;

    static
    {
    	fgSeparatorReplacement = File.separator.equals("/") ? "/" : "\\\\";
    }

    /**
     * Utility to normalize a path to platform specific form.
     * @param path
     * @return
     */
    private String normalizePath(String path)
    {
        path = path.replaceAll("/+", fgSeparatorReplacement);
        path = path.replace("/$", "");
        if (!path.startsWith(File.separator))
        {
            path = File.separator + path;
        }
        return path;
    }

    public SortedSet<FileDescriptor> getListing(String path)
    {
        Path cPath = new Path(path);
        StringBuilder builder = new StringBuilder();
        builder.append(fMetaDataDirectory);
        if (cPath.size() != 0)
        {
            for (int i = 0; i < cPath.size(); i++)
            {
                builder.append(File.separatorChar);
                builder.append(cPath.get(i));
            }
        }
        builder.append(File.separatorChar);
        builder.append(MD_NAME);
        String mdPath = builder.toString();
        return getDirectory(mdPath).getListing();
    }

    /**
     * Clone all the metadata files for the commit phase of a deployment.
     */
    public void cloneMetaData(Deployment deployment)
    {
        Set<String> toClone = new HashSet<String>();
        for (DeployedFile file : deployment)
        {
            Path path = new Path(file.getPath());
            if (file.getType() == FileType.DIR)
            {
                String pathString = fMetaDataDirectory + File.separatorChar + path.toString() + File.separatorChar + MD_NAME;
                toClone.add(pathString);
            }
            Path parent = path.getParent();
            String parentString = fMetaDataDirectory + File.separatorChar + parent.toString() + File.separatorChar + MD_NAME;
            toClone.add(parentString);
        }
        for (String path : toClone)
        {
            File file = new File(path);
            File dir = new File(file.getParent());     
            
            //Path filePath = new Path(path);
            //File dir = new File(filePath.getParent().toString());
            
            dir.mkdirs();
            DirectoryMetaData md = null;
            if (!file.exists())
            {
                md = new DirectoryMetaData();
            }
            else
            {
                md = getDirectory(path);
            }
            String cloneName = path + CLONE;
            putDirectory(cloneName, md);
        }
    }

    /**
     * Update cloned metadata file with the information given.
     * @param file
     */
    public void update(DeployedFile file)
    {
        Path path = new Path(file.getPath());
        Path parent = path.getParent();
        String mdName = fMetaDataDirectory + File.separatorChar + parent.toString() + File.separatorChar + MD_NAME + CLONE;
        DirectoryMetaData md = getDirectory(mdName);
        switch (file.getType())
        {
            case FILE :
            {
                FileDescriptor fd =
                    new FileDescriptor(path.getBaseName(),
                                       FileType.FILE,
                                       file.getGuid());
                md.remove(fd);
                md.add(fd);
                break;
            }
            case DIR :
            {
                FileDescriptor fd =
                    new FileDescriptor(path.getBaseName(),
                                       FileType.DIR,
                                       file.getGuid());
                md.remove(fd);
                md.add(fd);
                String newDirPath = fMetaDataDirectory + File.separatorChar + path.toString();
                File newDir = new File(newDirPath);
                newDir.mkdir();
                DirectoryMetaData newMD = new DirectoryMetaData();
                putDirectory(newDirPath + File.separatorChar + MD_NAME + CLONE, newMD);
                break;
            }
            case DELETED :
            {
                FileDescriptor toRemove = new FileDescriptor(path.getBaseName(),
                                                             FileType.DELETED,
                                                             null);
                md.remove(toRemove);
                md.add(toRemove);
                break;
            }
            case SETGUID :
            {
                FileDescriptor toModify = new FileDescriptor(path.getBaseName(),
                                                             null,
                                                             null);
                SortedSet<FileDescriptor> tail = md.getListing().tailSet(toModify);
                if (tail.size() != 0)
                {
                    toModify = tail.first();
                    if (toModify.getName().equals(path.getBaseName()))
                    {
                        toModify.setGuid(file.getGuid());
                        break;
                    }
                }
                throw new DeploymentException("Trying to set guid on non existent file " + path);
            }
            default :
            {
                throw new DeploymentException("Configuration Error: unknown FileType " + file.getType());
            }
        }
        putDirectory(mdName, md);
    }

    /**
     * Utility routine to get a metadata object.
     * @param path
     * @return
     */
    private DirectoryMetaData getDirectory(String path)
    {
        try
        {
            ObjectInputStream in = new ObjectInputStream(new FileInputStream(path));
            DirectoryMetaData md = (DirectoryMetaData)in.readObject();
            in.close();
            return md;
        }
        catch (IOException ioe)
        {
            throw new DeploymentException("Could not read metadata file " + path, ioe);
        }
        catch (ClassNotFoundException nfe)
        {
            throw new DeploymentException("Configuration error: could not instantiate DirectoryMetaData.");
        }
    }

    /**
     * Utility for writing a metadata object to disk.
     * @param path
     * @param md
     */
    private void putDirectory(String path, DirectoryMetaData md)
    {
        try
        {
            FileOutputStream fout = new FileOutputStream(path);
            ObjectOutputStream out = new ObjectOutputStream(fout);
            out.writeObject(md);
            out.flush();
            fout.getChannel().force(true);
            out.close();
        }
        catch (IOException ioe)
        {
            throw new DeploymentException("Could not write metadata file " + path, ioe);
        }
    }

    /**
     * Roll back metadata changes.
     */
    public void rollbackMetaData()
    {
        recursiveRollbackMetaData(fMetaDataDirectory);
    }

    /**
     * Commit changed metadata.
     */
    public void commitMetaData(Deployment deployment)
    {
        Set<String> toCommit = new HashSet<String>();
        for (DeployedFile file : deployment)
        {
            Path path = new Path(file.getPath());
            if (file.getType() == FileType.DIR)
            {
                toCommit.add(fMetaDataDirectory + File.separatorChar + path.toString() + File.separatorChar + MD_NAME);
            }
            String parent = fMetaDataDirectory + File.separatorChar + path.getParent().toString() +
                            File.separatorChar + MD_NAME;
            toCommit.add(parent);
        }
        for (String path : toCommit)
        {
            File original = new File(path);
            File old = new File(path + OLD);
            if (original.exists() && !original.renameTo(old))
            {
                throw new DeploymentException("Could not rename meta data file " + path);
            }
            DirectoryMetaData md = getDirectory(path + CLONE);
            List<FileDescriptor> toDelete = new ArrayList<FileDescriptor>();
            SortedSet<FileDescriptor> mdListing = md.getListing();
            for (FileDescriptor file : mdListing)
            {
                if (file.getType() == FileType.DELETED)
                {
                    toDelete.add(file);
                }
                else if (file.getType() == FileType.DIR)
                {
                    continue;
                }
                File mdDir = new File(path + File.separatorChar + file.getName());
                if (mdDir.exists())
                {
                    Deleter.Delete(mdDir);
                }
            }
            for (FileDescriptor file : toDelete)
            {
                md.remove(file);
            }
            putDirectory(path, md);
            old.delete();
            File clone = new File(path + CLONE);
            clone.delete();
        }
    }

    private void recursiveRollbackMetaData(String dir)
    {
        String mdName = dir + File.separatorChar + MD_NAME;
        String clone = mdName + CLONE;
        File dClone = new File(clone);
        dClone.delete();
        DirectoryMetaData md = getDirectory(mdName);
        SortedSet<FileDescriptor> mdListing = md.getListing();
        File dDir = new File(dir);
        File[] listing = dDir.listFiles();
        for (File entry : listing)
        {
            if (entry.isDirectory())
            {
                FileDescriptor dummy = new FileDescriptor(entry.getName(),
                                                          null,
                                                          null);
                if (!mdListing.contains(dummy))
                {
                    Deleter.Delete(entry);
                }
            }
        }
        listing = dDir.listFiles();
        for (File entry : listing)
        {
            if (entry.isDirectory())
            {
                recursiveRollbackMetaData(dir + File.separatorChar + entry.getName());
            }
        }
    }

    public void runPostCommit(Deployment deployment)
    {
        if (fRunnables != null && fRunnables.size() > 0)
        {
            for (FSDeploymentRunnable runnable : fRunnables)
            {
                try
                {
                    deployment.resetLog();
                    runnable.init(deployment);
                    runnable.run();
                }
                catch (IOException e)
                {
                    // Do Nothing for Now.
                }
            }
        }
    }
}
