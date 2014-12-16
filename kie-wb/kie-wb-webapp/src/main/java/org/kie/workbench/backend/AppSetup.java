/*
 * Copyright 2012 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.workbench.backend;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.inject.Inject;

import org.drools.workbench.screens.workitems.service.WorkItemsEditorService;
import org.guvnor.structure.organizationalunit.OrganizationalUnit;
import org.guvnor.structure.organizationalunit.OrganizationalUnitService;
import org.guvnor.structure.repositories.Repository;
import org.guvnor.structure.repositories.RepositoryService;
import org.guvnor.structure.server.config.ConfigGroup;
import org.guvnor.structure.server.config.ConfigItem;
import org.guvnor.structure.server.config.ConfigType;
import org.guvnor.structure.server.config.ConfigurationFactory;
import org.guvnor.structure.server.config.ConfigurationService;
import org.jbpm.console.ng.bd.service.AdministrationService;
import org.guvnor.common.services.shared.security.KieWorkbenchPolicy;
import org.guvnor.common.services.shared.security.impl.KieWorkbenchACLImpl;
import org.guvnor.common.services.shared.security.KieWorkbenchSecurityService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.uberfire.commons.services.cdi.ApplicationStarted;
import org.uberfire.commons.services.cdi.Startup;
import org.uberfire.commons.services.cdi.StartupType;
import org.uberfire.ext.security.server.RolesRegistry;

//This is a temporary solution when running in PROD-MODE as /webapp/.niogit/system.git folder
//is not deployed to the Application Servers /bin folder. This will be remedied when an
//installer is written to create the system.git repository in the correct location.
@Startup(StartupType.BOOTSTRAP)
@ApplicationScoped
public class AppSetup {

    private static final Logger logger = LoggerFactory.getLogger( AppSetup.class );

    // default repository section - start
    private static final String OU_NAME = "demo";
    private static final String OU_OWNER = "demo@demo.org";

    private static final String JBPM_WB_PLAYGROUND_ALIAS = "jbpm-playground";
    private static final String JBPM_WB_PLAYGROUND_ORIGIN = "https://github.com/guvnorngtestuser1/jbpm-console-ng-playground-kjar.git";
    private static final String JBPM_WB_PLAYGROUND_UID = "guvnorngtestuser1";
    private static final String JBPM_WB_PLAYGROUND_PWD = "test1234";

    private static final String DROOLS_WB_PLAYGROUND_ALIAS = "uf-playground";
    private static final String DROOLS_WB_PLAYGROUND_ORIGIN = "https://github.com/guvnorngtestuser1/guvnorng-playground.git";
    private static final String DROOLS_WB_PLAYGROUND_UID = "guvnorngtestuser1";
    private static final String DROOLS_WB_PLAYGROUND_PWD = "test1234";

    private static final String GLOBAL_SETTINGS = "settings";
    // default repository section - end

    @Inject
    private RepositoryService repositoryService;

    @Inject
    private OrganizationalUnitService organizationalUnitService;

    @Inject
    private ConfigurationService configurationService;

    @Inject
    private ConfigurationFactory configurationFactory;

    @Inject
    private AdministrationService administrationService;

    @Inject
    private Event<ApplicationStarted> applicationStartedEvent;

    @Inject
    private KieWorkbenchSecurityService securityService;

    @PostConstruct
    public void assertPlayground() {

        final String exampleRepositoriesRoot = System.getProperty( "org.kie.example.repositories" );
        if ( !( exampleRepositoriesRoot == null || "".equalsIgnoreCase( exampleRepositoriesRoot ) ) ) {
            loadExampleRepositories( exampleRepositoriesRoot );

        } else if ( !"false".equalsIgnoreCase( System.getProperty( "org.kie.demo" ) ) ) {
            administrationService.bootstrapRepository( OU_NAME,
                                                       JBPM_WB_PLAYGROUND_ALIAS,
                                                       JBPM_WB_PLAYGROUND_ORIGIN,
                                                       JBPM_WB_PLAYGROUND_UID,
                                                       JBPM_WB_PLAYGROUND_PWD );

            administrationService.bootstrapRepository( OU_NAME,
                                                       DROOLS_WB_PLAYGROUND_ALIAS,
                                                       DROOLS_WB_PLAYGROUND_ORIGIN,
                                                       DROOLS_WB_PLAYGROUND_UID,
                                                       DROOLS_WB_PLAYGROUND_PWD );

        } else if ( "true".equalsIgnoreCase( System.getProperty( "org.kie.example" ) ) ) {
            administrationService.bootstrapRepository( "example",
                                                       "repository1",
                                                       null,
                                                       "",
                                                       "" );
            administrationService.bootstrapProject( "repository1",
                                                    "org.kie.example",
                                                    "project1",
                                                    "1.0.0-SNAPSHOT" );
        }

        // Setup mandatory properties for Drools-Workbench
        List<ConfigGroup> configGroups = configurationService.getConfiguration( ConfigType.GLOBAL );
        boolean globalSettingsDefined = false;
        for ( ConfigGroup configGroup : configGroups ) {
            if ( GLOBAL_SETTINGS.equals( configGroup.getName() ) ) {
                globalSettingsDefined = true;
                ConfigItem<String> runtimeDeployConfig = configGroup.getConfigItem("support.runtime.deploy");
                if (runtimeDeployConfig == null) {
                    configGroup.addConfigItem( configurationFactory.newConfigItem( "support.runtime.deploy", "true" ) );
                    configurationService.updateConfiguration(configGroup);
                } else if (!runtimeDeployConfig.getValue().equalsIgnoreCase("true")) {
                    runtimeDeployConfig.setValue("true");
                    configurationService.updateConfiguration(configGroup);
                }
                break;
            }
        }
        if ( !globalSettingsDefined ) {
            configurationService.addConfiguration( getGlobalConfiguration() );
        }

        // Setup properties required by the Work Items Editor
        List<ConfigGroup> editorConfigGroups = configurationService.getConfiguration( ConfigType.EDITOR );
        boolean workItemsEditorSettingsDefined = false;
        for ( ConfigGroup editorConfigGroup : editorConfigGroups ) {
            if ( WorkItemsEditorService.WORK_ITEMS_EDITOR_SETTINGS.equals( editorConfigGroup.getName() ) ) {
                workItemsEditorSettingsDefined = true;
                break;
            }
        }
        if ( !workItemsEditorSettingsDefined ) {
            configurationService.addConfiguration( getWorkItemElementDefinitions() );
        }

        final KieWorkbenchPolicy policy = new KieWorkbenchPolicy( securityService.loadPolicy() );
        // register roles
        for ( final Map.Entry<String, String> entry : policy.entrySet() ) {
            if ( entry.getKey().startsWith( KieWorkbenchACLImpl.PREFIX_ROLES ) ) {
                String role = entry.getValue();
                RolesRegistry.get().registerRole( role );
            }
        }

        // rest of jbpm wb bootstrap
        administrationService.bootstrapConfig();
        administrationService.bootstrapDeployments();

        // notify components that bootstrap is completed to start post setups
        applicationStartedEvent.fire( new ApplicationStarted() );
    }

    private void loadExampleRepositories( final String exampleRepositoriesRoot ) {
        final File root = new File( exampleRepositoriesRoot );
        if ( !root.isDirectory() ) {
            logger.error( "System Property 'org.kie.example.repositories' does not point to a folder." );

        } else {
            //Create a new Organizational Unit
            logger.info( "Creating Organizational Unit '" + OU_NAME + "'." );
            OrganizationalUnit organizationalUnit = organizationalUnitService.getOrganizationalUnit( OU_NAME );
            if ( organizationalUnit == null ) {
                final List<Repository> repositories = new ArrayList<Repository>();
                organizationalUnit = organizationalUnitService.createOrganizationalUnit( OU_NAME,
                                                                                         OU_OWNER,
                                                                                         repositories );
                logger.info( "Created Organizational Unit '" + OU_NAME + "'." );

            } else {
                logger.info( "Organizational Unit '" + OU_NAME + "' already exists." );
            }

            final FileFilter filter = new FileFilter() {
                @Override
                public boolean accept( final File pathName ) {
                    return pathName.isDirectory();
                }
            };

            logger.info( "Cloning Example Repositories." );
            for ( File child : root.listFiles( filter ) ) {
                final String repositoryAlias = child.getName();
                final String repositoryOrigin = child.getAbsolutePath();
                logger.info( "Cloning Repository '" + repositoryAlias + "' from '" + repositoryOrigin + "'." );
                Repository repository = repositoryService.getRepository( repositoryAlias );
                if ( repository == null ) {
                    try {
                        repository = repositoryService.createRepository( "git",
                                                                         repositoryAlias,
                                                                         new HashMap<String, Object>() {{
                                                                             put( "origin", repositoryOrigin );
                                                                         }} );
                        organizationalUnitService.addRepository( organizationalUnit,
                                                                 repository );
                    } catch ( Exception e ) {
                        logger.error( "Failed to clone Repository '" + repositoryAlias + "'",
                                      e );
                    }
                } else {
                    logger.info( "Repository '" + repositoryAlias + "' already exists." );
                }
            }
            logger.info( "Example Repositories cloned." );
        }
    }

    private ConfigGroup getGlobalConfiguration() {
        final ConfigGroup group = configurationFactory.newConfigGroup( ConfigType.GLOBAL,
                                                                       GLOBAL_SETTINGS,
                                                                       "" );
        group.addConfigItem( configurationFactory.newConfigItem( "drools.dateformat",
                                                                 "dd-MMM-yyyy" ) );
        group.addConfigItem( configurationFactory.newConfigItem( "drools.datetimeformat",
                                                                 "dd-MMM-yyyy hh:mm:ss" ) );
        group.addConfigItem( configurationFactory.newConfigItem( "drools.defaultlanguage",
                                                                 "en" ) );
        group.addConfigItem( configurationFactory.newConfigItem( "drools.defaultcountry",
                                                                 "US" ) );
        group.addConfigItem( configurationFactory.newConfigItem( "build.enable-incremental",
                                                                 "true" ) );
        group.addConfigItem( configurationFactory.newConfigItem( "rule-modeller-onlyShowDSLStatements",
                                                                 "false" ) );
        group.addConfigItem( configurationFactory.newConfigItem( "designer.context",
                                                                 "designer" ) );
        group.addConfigItem( configurationFactory.newConfigItem( "designer.profile",
                                                                 "jbpm" ) );
        group.addConfigItem( configurationFactory.newConfigItem( "support.runtime.deploy",
                                                                 "true" ) );
        return group;
    }

    private ConfigGroup getWorkItemElementDefinitions() {
        // Work Item Definition elements used when creating Work Item Definitions.
        // Each entry in this file represents a Button in the Editor's Palette:-
        //   - Underscores ('_') in the key will be converted in whitespaces (' ') and
        //     will be used as Button's labels.
        //   - The value will be the text pasted into the editor when an element in the
        //     palette is selected. You can use a pipe ('|') to specify the place where
        //     the cursor should be put after pasting the element into the editor.
        final ConfigGroup group = configurationFactory.newConfigGroup( ConfigType.EDITOR,
                                                                       WorkItemsEditorService.WORK_ITEMS_EDITOR_SETTINGS,
                                                                       "" );
        group.addConfigItem( configurationFactory.newConfigItem( WorkItemsEditorService.WORK_ITEMS_EDITOR_SETTINGS_DEFINITION,
                                                                 "import org.drools.core.process.core.datatype.impl.type.StringDataType;\n" +
                                                                         "import org.drools.core.process.core.datatype.impl.type.ObjectDataType;\n" +
                                                                         "\n" +
                                                                         "[\n" +
                                                                         "  [\n" +
                                                                         "    \"name\" : \"MyTask|\", \n" +
                                                                         "    \"parameters\" : [ \n" +
                                                                         "        \"MyFirstParam\" : new StringDataType(), \n" +
                                                                         "        \"MySecondParam\" : new StringDataType(), \n" +
                                                                         "        \"MyThirdParam\" : new ObjectDataType() \n" +
                                                                         "    ], \n" +
                                                                         "    \"results\" : [ \n" +
                                                                         "        \"Result\" : new ObjectDataType(\"java.util.Map\") \n" +
                                                                         "    ], \n" +
                                                                         "    \"displayName\" : \"My Task\", \n" +
                                                                         "    \"icon\" : \"\" \n" +
                                                                         "  ]\n" +
                                                                         "]" ) );
        group.addConfigItem( configurationFactory.newConfigItem( WorkItemsEditorService.WORK_ITEMS_EDITOR_SETTINGS_PARAMETER,
                                                                 "\"MyParam|\" : new StringDataType()" ) );
        group.addConfigItem( configurationFactory.newConfigItem( WorkItemsEditorService.WORK_ITEMS_EDITOR_SETTINGS_RESULT,
                                                                 "\"Result|\" : new ObjectDataType()" ) );
        group.addConfigItem( configurationFactory.newConfigItem( WorkItemsEditorService.WORK_ITEMS_EDITOR_SETTINGS_DISPLAY_NAME,
                                                                 "\"displayName\" : \"My Task|\"" ) );
        return group;
    }

}
