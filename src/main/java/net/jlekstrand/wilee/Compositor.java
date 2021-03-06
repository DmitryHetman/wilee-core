/*
 * Copyright © 2012-2013 Jason Ekstrand.
 *  
 * Permission to use, copy, modify, distribute, and sell this software and its
 * documentation for any purpose is hereby granted without fee, provided that
 * the above copyright notice appear in all copies and that both that copyright
 * notice and this permission notice appear in supporting documentation, and
 * that the name of the copyright holders not be used in advertising or
 * publicity pertaining to distribution of the software without specific,
 * written prior permission.  The copyright holders make no representations
 * about the suitability of this software for any purpose.  It is provided "as
 * is" without express or implied warranty.
 * 
 * THE COPYRIGHT HOLDERS DISCLAIM ALL WARRANTIES WITH REGARD TO THIS SOFTWARE,
 * INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS, IN NO
 * EVENT SHALL THE COPYRIGHT HOLDERS BE LIABLE FOR ANY SPECIAL, INDIRECT OR
 * CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE,
 * DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER
 * TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE
 * OF THIS SOFTWARE.
 */
package net.jlekstrand.wilee;

import net.jlekstrand.wilee.graphics.*;

import java.lang.Runnable;
import java.lang.Thread;
import java.util.ListIterator;
import java.io.File;

import org.freedesktop.wayland.server.EventLoop;
import org.freedesktop.wayland.server.Display;
import org.freedesktop.wayland.server.Global;
import org.freedesktop.wayland.server.Client;
import org.freedesktop.wayland.server.Resource;

import org.freedesktop.wayland.protocol.wl_compositor;
import org.freedesktop.wayland.protocol.wl_shm;

public class Compositor implements wl_compositor.Requests
{
    private static final String LOG_TAG = "Compositor";

    public final Display display;
    private final Global global;

    protected Shm shm;
    protected Shell shell;
    protected DataDeviceManager dataDeviceManager;
    protected Renderer renderer;

    private boolean render_pending;
    private EventLoopQueuedExecutor jobExecutor;
    private Thread compositorThread;

    public Compositor()
    {
        renderer = null;

        display = new Display();
        global = new Global(display, wl_compositor.WAYLAND_INTERFACE, 1,
                new Global.BindHandler()
        {
            @Override
            public void bindClient(Client client, int version, int id)
            {
                Resource res = new wl_compositor.Resource(client, version, id);
                res.setImplementation(Compositor.this);
            }
        });

        try {
            jobExecutor = new EventLoopQueuedExecutor();
            jobExecutor.addToEventLoop(display.getEventLoop());
        } catch (java.io.IOException e) {
            throw new RuntimeException(e);
        }

        shm = new Shm(display);
        new DataDeviceManager(display);
        shell = new DesktopShell(display);
    }

    public Display getDisplay()
    {
        return display;
    }

    public void run()
    {
        compositorThread = Thread.currentThread();
        display.run();
    }

    public void queueEvent(final Runnable runnable)
    {
        if (Thread.currentThread() == compositorThread) {
            display.getEventLoop().addIdle(new EventLoop.IdleHandler() {
                public void handleIdle()
                {
                    runnable.run();
                }
            });
        } else {
            jobExecutor.execute(runnable);
        }
    }

    private void doRender()
    {
        if (renderer == null)
            return;

        final ListIterator<Surface> iter = shell.getVisibleSurfaces();

        while (iter.hasNext())
            iter.next();

        renderer.beginRender(true);

        while (iter.hasPrevious())
            renderer.drawSurface(iter.previous());

        final int timestamp = renderer.endRender();

        while (iter.hasNext())
            iter.next().frameDrawn(timestamp);

        display.flushClients();
    }

    private void requestRender()
    {
        if (render_pending)
            return;

        render_pending = true;
        display.getEventLoop().addIdle(new EventLoop.IdleHandler() {
            @Override
            public void handleIdle()
            {
                if (render_pending)
                    doRender();
                render_pending = false;
            }
        });
    }

    public void setRenderer(Renderer renderer)
    {
        this.renderer = renderer;

        if (renderer != null) {
            queueEvent(new Runnable() {
                @Override
                public void run()
                {
                    requestRender();
                }
            });
        }
    }

    public void surfaceDamaged(Surface surface, Region damage)
    {
        boolean needs_redraw = shell.surfaceDamaged(surface, damage);

        if (needs_redraw)
            requestRender();
    }

    public Surface findSurfaceAt(Point p)
    {
        final ListIterator<Surface> iter = shell.getVisibleSurfaces();

        while (iter.hasNext()) {
            Surface surface = iter.next();
            Matrix3 invTrans = surface.getInverseTransform();
            if (invTrans == null)
                continue;

            if (surface.isInInputRegion(p.transform(invTrans)))
                return surface;
        }

        return null;
    }

    @Override
    public void createSurface(wl_compositor.Resource resource, int id)
    {
        new ClientSurface(resource.getClient(), id, this);
    }

    @Override
    public void createRegion(wl_compositor.Resource resource, int id)
    {
        new ClientRegion(resource.getClient(), id);
    }
}

