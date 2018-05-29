/**
 * Copyright (C) 2006-2018 Talend Inc. - www.talend.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.talend.sdk.component.proxy.service;

import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.toMap;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static org.talend.sdk.component.proxy.model.ProxyErrorDictionary.NO_COMPONENT_IN_FAMILY;
import static org.talend.sdk.component.proxy.model.ProxyErrorDictionary.NO_FAMILY_FOR_CONFIGURATION;

import java.util.Map;
import java.util.stream.Stream;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.talend.sdk.component.proxy.client.ConfigurationClient;
import org.talend.sdk.component.proxy.model.Node;
import org.talend.sdk.component.proxy.model.Nodes;
import org.talend.sdk.component.proxy.model.ProxyErrorPayload;
import org.talend.sdk.component.server.front.model.ComponentIndices;
import org.talend.sdk.component.server.front.model.ConfigTypeNode;
import org.talend.sdk.component.server.front.model.ConfigTypeNodes;

/**
 * This service encapsulate all the logic of the configuration transformation
 */
@ApplicationScoped
public class ConfigurationService {

    @Inject
    private ConfigurationClient client;

    public Nodes getRootConfiguration(final ConfigTypeNodes configs, final ComponentIndices componentIndices) {

        final Map<String, ConfigTypeNode> families = ofNullable(configs)
                .map(c -> c.getNodes().values().stream())
                .orElseGet(Stream::empty)
                .filter(node -> node.getParentId() == null)
                .collect(toMap(ConfigTypeNode::getId, identity()));
        return new Nodes(configs
                .getNodes()
                .values()
                .stream()
                .flatMap(node -> node.getEdges().stream().map(edgeId -> configs.getNodes().get(edgeId)).filter(
                        config -> config.getParentId() != null && families.containsKey(config.getParentId())))
                .map(root -> {
                    final ConfigTypeNode family = families.get(root.getParentId());
                    return new Node(root.getId(), Node.Type.CONFIGURATION, root.getDisplayName(), family.getId(),
                            family.getDisplayName(), findIcon(family, componentIndices), root.getEdges(),
                            root.getVersion(), root.getName(), null);
                })
                .collect(toMap(Node::getId, identity())));

    }

    public ConfigTypeNode getFamilyOf(final String id, final ConfigTypeNodes nodes) {
        ConfigTypeNode family = nodes.getNodes().get(id);
        while (family != null && family.getParentId() != null) {
            family = nodes.getNodes().get(family.getParentId());
        }

        if (family == null) {
            throw new WebApplicationException(Response
                    .status(INTERNAL_SERVER_ERROR)
                    .entity(new ProxyErrorPayload(NO_FAMILY_FOR_CONFIGURATION.name(),
                            "No family found for this configuration identified by id:" + id))
                    .header(ErrorProcessor.Constants.HEADER_TALEND_COMPONENT_SERVER_ERROR, false)
                    .build());
        }

        return family;
    }

    public String findIcon(final ConfigTypeNode family, final ComponentIndices componentIndices) {
        return componentIndices
                .getComponents()
                .stream()
                .filter(component -> component.getId().getFamilyId().equals(family.getId()))
                .findFirst()
                .orElseThrow(() -> new WebApplicationException(Response
                        .status(INTERNAL_SERVER_ERROR)
                        .entity(new ProxyErrorPayload(NO_COMPONENT_IN_FAMILY.name(),
                                "No component found in this family " + family))
                        .header(ErrorProcessor.Constants.HEADER_TALEND_COMPONENT_SERVER_ERROR, false)
                        .build()))
                .getIconFamily()
                .getIcon();
    }
}
