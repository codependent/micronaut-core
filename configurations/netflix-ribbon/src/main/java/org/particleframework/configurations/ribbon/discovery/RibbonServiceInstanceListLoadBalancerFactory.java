/*
 * Copyright 2018 original authors
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
package org.particleframework.configurations.ribbon.discovery;

import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.*;
import org.particleframework.configurations.ribbon.RibbonLoadBalancer;
import org.particleframework.configurations.ribbon.RibbonServer;
import org.particleframework.context.BeanContext;
import org.particleframework.context.annotation.Replaces;
import org.particleframework.discovery.ServiceInstance;
import org.particleframework.discovery.ServiceInstanceList;
import org.particleframework.http.client.LoadBalancer;
import org.particleframework.http.client.loadbalance.ServiceInstanceListLoadBalancerFactory;
import org.particleframework.inject.qualifiers.Qualifiers;

import javax.inject.Singleton;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Replaces the default {@link ServiceInstanceListLoadBalancerFactory} with one that returns {@link RibbonLoadBalancer} instances
 *
 * @author graemerocher
 * @since 1.0
 */
@Singleton
@Replaces(ServiceInstanceListLoadBalancerFactory.class)
public class RibbonServiceInstanceListLoadBalancerFactory extends ServiceInstanceListLoadBalancerFactory {
    private final BeanContext beanContext;
    private final IClientConfig defaultClientConfig;

    public RibbonServiceInstanceListLoadBalancerFactory(BeanContext beanContext, IClientConfig defaultClientConfig) {
        this.beanContext = beanContext;
        this.defaultClientConfig = defaultClientConfig;
    }

    @Override
    public LoadBalancer create(ServiceInstanceList serviceInstanceList) {
        String serviceID = serviceInstanceList.getID();
        IClientConfig niwsClientConfig = beanContext.findBean(IClientConfig.class, Qualifiers.byName(serviceID)).orElse(defaultClientConfig);
        IRule rule = beanContext.findBean(IRule.class, Qualifiers.byName(serviceID)).orElseGet(()->beanContext.createBean(IRule.class));
        IPing ping = beanContext.findBean(IPing.class, Qualifiers.byName(serviceID)).orElseGet(()->beanContext.createBean(IPing.class));
        ServerListFilter serverListFilter = beanContext.findBean(ServerListFilter.class, Qualifiers.byName(serviceID)).orElseGet(()-> beanContext.createBean(ServerListFilter.class));
        ServerList<Server> serverList = beanContext.findBean(ServerList.class, Qualifiers.byName(serviceID)).orElseGet(()-> toRibbonServerList(serviceInstanceList));

        if(niwsClientConfig.getPropertyAsBoolean(CommonClientConfigKey.InitializeNFLoadBalancer, true)) {
            return createRibbonLoadBalancer(niwsClientConfig, rule, ping, serverListFilter, serverList);
        }
        else {
            return super.create(serviceInstanceList);
        }

    }

    private ServerList toRibbonServerList(ServiceInstanceList serviceInstanceList) {
        return new AbstractServerList<Server>() {
            @Override
            public void initWithNiwsConfig(IClientConfig clientConfig) {

            }

            @Override
            public List<Server> getInitialListOfServers() {
                List<ServiceInstance> instances = serviceInstanceList.getInstances();
                return instances.stream().map(RibbonServer::new).collect(Collectors.toList());
            }

            @Override
            public List<Server> getUpdatedListOfServers() {
                return getInitialListOfServers();
            }
        };
    }

    protected RibbonLoadBalancer createRibbonLoadBalancer(IClientConfig niwsClientConfig, IRule rule, IPing ping, ServerListFilter serverListFilter, ServerList<Server> serverList) {
        return new RibbonLoadBalancer(
                niwsClientConfig,
                serverList,
                serverListFilter,
                rule,
                ping
        );
    }

}