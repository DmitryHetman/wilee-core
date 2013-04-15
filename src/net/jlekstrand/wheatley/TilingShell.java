package net.jlekstrand.wheatley;

import java.util.LinkedList;

import android.util.Log;
import android.graphics.Region;

import org.freedesktop.wayland.server.Global;
import org.freedesktop.wayland.server.Client;
import org.freedesktop.wayland.server.Listener;
import org.freedesktop.wayland.server.Resource;

import org.freedesktop.wayland.protocol.wl_shell;
import org.freedesktop.wayland.protocol.wl_surface;

class TilingShell extends Global implements Shell
{
    private static final String LOG_PREFIX = "TilingShell";

    private LinkedList<ShellSurface> surfaces;

    public TilingShell()
    {
        super(wl_shell.WAYLAND_INTERFACE);

        surfaces = new LinkedList<ShellSurface>();
    }

    public void render(Renderer renderer)
    {
        renderer.beginRender(true);

        for (ShellSurface ssurface : surfaces)
            renderer.drawSurface(ssurface.surface);

        int serial = renderer.endRender();

        for (ShellSurface ssurface : surfaces)
            ssurface.surface.notifyFrameCallbacks(serial);
    }

    @Override
    public boolean surfaceDamaged(Surface surface, Region damage)
    {
        /* Always redraw */
        return true;
    }

    @Override
    public void bindClient(Client client, int version, int id)
    {
        client.addObject(wl_shell.WAYLAND_INTERFACE, id, this);
    }

    @Override
    public void getShellSurface(wl_shell.Resource resource, int id,
            Resource surfaceRes)
    {
        final ShellSurface ssurface = new ShellSurface(resource.getClient(),
                id, (Surface)surfaceRes.getData());

        surfaces.add(ssurface);
        ssurface.resource.addDestroyListener(new Listener() {
            @Override
            public void onNotify()
            {
                surfaces.remove(ssurface);
            }
        });
    }
}

