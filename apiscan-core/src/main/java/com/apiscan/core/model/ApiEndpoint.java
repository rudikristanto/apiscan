package com.apiscan.core.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ApiEndpoint {
    private String path;
    private String httpMethod;
    private String operationId;
    private String summary;
    private String description;
    private List<String> tags = new ArrayList<>();
    private List<Parameter> parameters = new ArrayList<>();
    private Map<String, Response> responses = new HashMap<>();
    private RequestBody requestBody;
    private String controllerClass;
    private String methodName;
    private List<String> consumes = new ArrayList<>();
    private List<String> produces = new ArrayList<>();
    private boolean deprecated;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public String getOperationId() {
        return operationId;
    }

    public void setOperationId(String operationId) {
        this.operationId = operationId;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<Parameter> getParameters() {
        return parameters;
    }

    public void setParameters(List<Parameter> parameters) {
        this.parameters = parameters;
    }

    public Map<String, Response> getResponses() {
        return responses;
    }

    public void setResponses(Map<String, Response> responses) {
        this.responses = responses;
    }

    public RequestBody getRequestBody() {
        return requestBody;
    }

    public void setRequestBody(RequestBody requestBody) {
        this.requestBody = requestBody;
    }

    public String getControllerClass() {
        return controllerClass;
    }

    public void setControllerClass(String controllerClass) {
        this.controllerClass = controllerClass;
    }

    public String getMethodName() {
        return methodName;
    }

    public void setMethodName(String methodName) {
        this.methodName = methodName;
    }

    public List<String> getConsumes() {
        return consumes;
    }

    public void setConsumes(List<String> consumes) {
        this.consumes = consumes;
    }

    public List<String> getProduces() {
        return produces;
    }

    public void setProduces(List<String> produces) {
        this.produces = produces;
    }

    public boolean isDeprecated() {
        return deprecated;
    }

    public void setDeprecated(boolean deprecated) {
        this.deprecated = deprecated;
    }

    public static class Parameter {
        private String name;
        private String in; // query, path, header, cookie
        private String description;
        private boolean required;
        private String type;
        private String schema;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getIn() {
            return in;
        }

        public void setIn(String in) {
            this.in = in;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public String getType() {
            return type;
        }

        public void setType(String type) {
            this.type = type;
        }

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }
    }

    public static class RequestBody {
        private String description;
        private boolean required;
        private Map<String, MediaType> content = new HashMap<>();

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public Map<String, MediaType> getContent() {
            return content;
        }

        public void setContent(Map<String, MediaType> content) {
            this.content = content;
        }
    }

    public static class Response {
        private String description;
        private Map<String, MediaType> content = new HashMap<>();

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Map<String, MediaType> getContent() {
            return content;
        }

        public void setContent(Map<String, MediaType> content) {
            this.content = content;
        }
    }

    public static class MediaType {
        private String schema;

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }
    }
}