/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.falcon.resource;

import org.apache.commons.lang3.StringUtils;
import org.apache.falcon.FalconException;
import org.apache.falcon.FalconWebException;
import org.apache.falcon.entity.parser.ValidationException;
import org.apache.falcon.extensions.jdbc.ExtensionMetaStore;
import org.apache.falcon.extensions.store.ExtensionStore;
import org.apache.falcon.persistence.ExtensionBean;
import org.apache.falcon.persistence.ExtensionJobsBean;
import org.apache.falcon.security.CurrentUser;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.Response;
import java.util.List;

/**
 * A base class for managing Extension Operations.
 */
public class AbstractExtensionManager extends AbstractSchedulableEntityManager {
    public static final Logger LOG = LoggerFactory.getLogger(AbstractExtensionManager.class);

    private static final String JOB_NAME = "jobName";
    protected static final String TAG_PREFIX_EXTENSION_JOB = "_falcon_extension_job=";
    private static final String EXTENSION_NAME = "extensionName";
    private static final String FEEDS = "feeds";
    private static final String PROCESSES = "processes";
    private static final String CONFIG  = "config";
    private static final String CREATION_TIME  = "creationTime";
    private static final String LAST_UPDATE_TIME  = "lastUpdatedTime";

    public static final String NAME = "name";
    protected static final String EXTENSION_TYPE = "type";
    protected static final String EXTENSION_DESC = "description";
    protected static final String EXTENSION_LOCATION = "location";

    protected static void validateExtensionName(final String extensionName) {
        if (StringUtils.isBlank(extensionName)) {
            throw FalconWebException.newAPIException("Extension name is mandatory and shouldn't be blank",
                    Response.Status.BAD_REQUEST);
        }
    }

    protected APIResult registerExtensionMetadata(String extensionName, String path, String description, String owner) {
        validateExtensionName(extensionName);
        try {
            return new APIResult(APIResult.Status.SUCCEEDED, ExtensionStore.get().registerExtension(extensionName, path,
                    description, owner));
        } catch (Throwable e) {
            throw FalconWebException.newAPIException(e, Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    public APIResult getExtensionJobDetail(String jobName) {
        try {
            return new APIResult(APIResult.Status.SUCCEEDED, buildExtensionJobDetailResult(jobName).toString());
        } catch (FalconException e) {
            throw FalconWebException.newAPIException(e, Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    protected APIResult getExtensionDetail(String extensionName) {
        try {
            return new APIResult(APIResult.Status.SUCCEEDED, buildExtensionDetailResult(extensionName).toString());
        } catch (FalconException e) {
            throw FalconWebException.newAPIException(e, Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    public APIResult getExtensions() {
        try {
            return new APIResult(APIResult.Status.SUCCEEDED, buildEnumerateResult().toString());
        } catch (FalconException e) {
            throw FalconWebException.newAPIException(e, Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    public APIResult deleteExtensionMetadata(String extensionName){
        validateExtensionName(extensionName);
        try {
            return new APIResult(APIResult.Status.SUCCEEDED, ExtensionStore.get().deleteExtension(extensionName,
                    CurrentUser.getUser()));
        } catch (Throwable e) {
            throw FalconWebException.newAPIException(e, Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    private JSONObject buildExtensionJobDetailResult(final String jobName) throws FalconException {
        ExtensionMetaStore metaStore = ExtensionStore.getMetaStore();
        ExtensionJobsBean jobsBean = metaStore.getExtensionJobDetails(jobName);
        if (jobsBean == null) {
            throw new ValidationException("Job name not found:" + jobName);
        }
        ExtensionBean extensionBean = metaStore.getDetail(jobsBean.getExtensionName());
        JSONObject detailsObject = new JSONObject();
        try {
            detailsObject.put(JOB_NAME, jobsBean.getJobName());
            detailsObject.put(EXTENSION_NAME, jobsBean.getExtensionName());
            detailsObject.put(FEEDS, StringUtils.join(jobsBean.getFeeds(), ","));
            detailsObject.put(PROCESSES, StringUtils.join(jobsBean.getProcesses(), ","));
            detailsObject.put(CONFIG, jobsBean.getConfig());
            detailsObject.put(CREATION_TIME, jobsBean.getCreationTime());
            detailsObject.put(LAST_UPDATE_TIME, jobsBean.getLastUpdatedTime());
            detailsObject.put(EXTENSION_LOCATION, extensionBean.getLocation());
            detailsObject.put(EXTENSION_TYPE, extensionBean.getExtensionType());
        } catch (JSONException e) {
            LOG.error("Exception while building extension jon details for job {}", jobName, e);
        }
        return detailsObject;
    }

    public static String getJobNameFromTag(String tags) {
        int nameStart = tags.indexOf(TAG_PREFIX_EXTENSION_JOB);
        if (nameStart == -1) {
            return null;
        }

        nameStart = nameStart + TAG_PREFIX_EXTENSION_JOB.length();
        int nameEnd = tags.indexOf(',', nameStart);
        if (nameEnd == -1) {
            nameEnd = tags.length();
        }
        return tags.substring(nameStart, nameEnd);
    }

    private JSONObject buildExtensionDetailResult(final String extensionName) throws FalconException {
        ExtensionMetaStore metaStore = ExtensionStore.getMetaStore();

        if (!metaStore.checkIfExtensionExists(extensionName)){
            throw new ValidationException("No extension resources found for " + extensionName);
        }

        ExtensionBean bean = metaStore.getDetail(extensionName);
        JSONObject resultObject = new JSONObject();
        try {
            resultObject.put(NAME, bean.getExtensionName());
            resultObject.put(EXTENSION_TYPE, bean.getExtensionType());
            resultObject.put(EXTENSION_DESC, bean.getDescription());
            resultObject.put(EXTENSION_LOCATION, bean.getLocation());
        } catch (JSONException e) {
            LOG.error("Exception in buildDetailResults:", e);
            throw new FalconException(e);
        }
        return resultObject;
    }

    private static JSONArray buildEnumerateResult() throws FalconException {
        JSONArray results = new JSONArray();
        ExtensionMetaStore metaStore = ExtensionStore.getMetaStore();
        List<ExtensionBean> extensionBeanList = metaStore.getAllExtensions();
        for (ExtensionBean extensionBean : extensionBeanList) {
            JSONObject resultObject = new JSONObject();

            try {
                resultObject.put(NAME, extensionBean.getExtensionName().toLowerCase());
                resultObject.put(EXTENSION_TYPE, extensionBean.getExtensionType());
                resultObject.put(EXTENSION_DESC, extensionBean.getDescription());
                resultObject.put(EXTENSION_LOCATION, extensionBean.getLocation());
            } catch (JSONException e) {
                throw new FalconException(e);
            }
            results.put(resultObject);
        }
        return results;
    }
}