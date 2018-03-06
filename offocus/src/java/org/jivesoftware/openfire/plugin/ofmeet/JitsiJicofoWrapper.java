/*
 * Copyright (c) 2017 Ignite Realtime Foundation. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jivesoftware.openfire.plugin.ofmeet;

import net.java.sip.communicator.util.ServiceUtils;
import org.igniterealtime.openfire.plugin.ofmeet.config.OFMeetConfig;
import org.jitsi.jicofo.FocusBundleActivator;
import org.jitsi.jicofo.FocusManager;
import org.jitsi.jicofo.JvbDoctor;
import org.jitsi.jicofo.auth.AuthenticationAuthority;
import org.jitsi.jicofo.osgi.JicofoBundleConfig;
import org.jitsi.jicofo.reservation.ReservationSystem;
import org.jitsi.jicofo.xmpp.FocusComponent;
import org.jitsi.meet.OSGi;
import org.jitsi.meet.OSGiBundleConfig;
import org.jivesoftware.openfire.XMPPServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmpp.component.ComponentManagerFactory;

/**
 * A wrapper object for the Jitsi Component Focus (jicofo) component.
 *
 * This wrapper can be used to instantiate/initialize and tearing down an instance of the wrapped component. An instance
 * of this class is re-usable.
 *
 * @author Guus der Kinderen, guus.der.kinderen@gmail.com
 */
public class JitsiJicofoWrapper
{
    private static final Logger Log = LoggerFactory.getLogger( JitsiJicofoWrapper.class );

    private String jicofoSubdomain = "focus";

    private FocusComponent jicofoComponent;

    /**
     * Initialize the wrapped component.
     *
     * @throws Exception On any problem.
     */
    public synchronized void initialize() throws Exception
    {
        Log.debug( "Initializing Jitsi Focus Component (jicofo)...");

        final OFMeetConfig config = new OFMeetConfig();
        if ( jicofoComponent != null )
        {
            Log.warn( "Another Jitsi Focus Component (jicofo) appears to have been initialized earlier! Unexpected behavior might be the result of this new initialization!" );
        }

        System.setProperty( FocusManager.HOSTNAME_PNAME, XMPPServer.getInstance().getServerInfo().getHostname() );
        System.setProperty( FocusManager.XMPP_DOMAIN_PNAME, XMPPServer.getInstance().getServerInfo().getXMPPDomain() );
        System.setProperty( FocusManager.FOCUS_USER_DOMAIN_PNAME, XMPPServer.getInstance().getServerInfo().getXMPPDomain() );
        if ( config.getFocusPassword() == null )
        {
            Log.warn( "No password is configured for the 'focus'. This is likely going to cause problems in webRTC meetings." );
        }
        else
        {
            System.setProperty( FocusManager.FOCUS_USER_NAME_PNAME, "focus" );
            System.setProperty( FocusManager.FOCUS_USER_PASSWORD_PNAME, config.getFocusPassword() );
        }

        // Jicofo should trust any certificates (as it is communicating with the local Openfire instance only, which we can safely define as 'trusted').
        System.setProperty( "net.java.sip.communicator.service.gui.ALWAYS_TRUST_MODE_ENABLED", Boolean.toString( true ) );
        System.setProperty( FocusManager.ALWAYS_TRUST_PNAME, Boolean.toString( true ) );

        // Disable health check. Our JVB is not an external component, so there's no need to check for its connectivity.
        // Also, the health check appears to cumulatively use and not release resources!
        System.setProperty( JvbDoctor.HEALTH_CHECK_INTERVAL_PNAME, "-1" );
        System.setProperty( "org.jitsi.jicofo.PING_INTERVAL", "-1" );

        // Disable JVB rediscovery. We are running with one hard-coded videobridge, there's no need for dynamic detection of others.
        System.setProperty( "org.jitsi.jicofo.SERVICE_REDISCOVERY_INTERVAL", "-1" ); // Aught to use a reference to ComponentsDiscovery.REDISCOVERY_INTERVAL_PNAME, but that constant is private.

        // Typically, the focus user is a system user (our plugin provisions the user), but if that fails, anonymous authentication will be used.
        final boolean focusAnonymous = config.getFocusPassword() == null;

        // Start the OSGi bundle for Jicofo.
        final OSGiBundleConfig jicofoConfig = new JicofoBundleConfig();
        OSGi.setBundleConfig(jicofoConfig);

        jicofoComponent = new FocusComponent( XMPPServer.getInstance().getServerInfo().getHostname(), 0, XMPPServer.getInstance().getServerInfo().getXMPPDomain(), jicofoSubdomain, null, focusAnonymous, XMPPServer.getInstance().createJID( "focus", null ).toBareJID() );

        Thread.sleep(2000 ); // Intended to prevent ConcurrentModificationExceptions while starting the component. See https://github.com/igniterealtime/ofmeet-openfire-plugin/issues/4
        jicofoComponent.init(); // Note that this is a Jicoco special, not Component#initialize!
        Thread.sleep(2000 ); // Intended to prevent ConcurrentModificationExceptions while starting the component. See https://github.com/igniterealtime/ofmeet-openfire-plugin/issues/4

        ComponentManagerFactory.getComponentManager().addComponent(jicofoSubdomain, jicofoComponent);

        Log.trace( "Successfully initialized Jitsi Focus Component (jicofo).");
    }

    /**
     * Destroying the wrapped component. After this call, the wrapped component can be re-initialized.
     *
     * @throws Exception On any problem.
     */
    public synchronized void destroy() throws Exception
    {
        Log.debug( "Destroying Jitsi Focus Component..." );

        if ( jicofoComponent == null)
        {
            Log.warn( "Unable to destroy the Jitsi Focus Component, as none appears to be running!" );
        }
        else
        {
            ComponentManagerFactory.getComponentManager().removeComponent(jicofoSubdomain);
            jicofoSubdomain = null;

            jicofoComponent.dispose();
            jicofoComponent = null;

        }

        Log.trace( "Successfully destroyed Jitsi Focus Component.   " );
    }

    public FocusComponent getFocusComponent()
    {
        return jicofoComponent;
    }

    public ReservationSystem getReservationService()
    {
        return ServiceUtils.getService(FocusBundleActivator.bundleContext, ReservationSystem.class);
    }

    public FocusManager getFocusManager()
    {
        return ServiceUtils.getService(FocusBundleActivator.bundleContext, FocusManager.class);
    }

    public AuthenticationAuthority getAuthenticationAuthority()
    {
        return ServiceUtils.getService( FocusBundleActivator.bundleContext, AuthenticationAuthority.class);
    }
}
