package net.jlekstrand.wayland.compositor;

import java.util.LinkedList;

import android.util.Log;
import android.graphics.Region;

import org.freedesktop.wayland.server.Global;
import org.freedesktop.wayland.server.Client;
import org.freedesktop.wayland.server.Listener;

import org.freedesktop.wayland.protocol.wl_shell;
import org.freedesktop.wayland.protocol.wl_surface;

class TilingShell implements Shell, Global.BindHandler
{
    private static final String LOG_PREFIX = "Wayland:TilingShell";

    private LinkedList<ShellSurface> surfaces;

    public TilingShell()
    {
        surfaces = new LinkedList<ShellSurface>();
    }

    public Global getGlobal()
    {
        return new Global(wl_shell.WAYLAND_INTERFACE, this);
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
        Log.d(LOG_PREFIX, "Binding Tiling Shell object");
        client.addObject(wl_shell.WAYLAND_INTERFACE, id, this);
    }

    @Override
    public void getShellSurface(Client client, int id,
            wl_surface.Requests surfaceReq)
    {
        final ShellSurface ssurface = new ShellSurface(id, (Surface)surfaceReq);
        surfaces.add(ssurface);
        ssurface.addDestroyListener(new Listener() {
            @Override
            public void onNotify()
            {
                surfaces.remove(ssurface);
            }
        });
        Log.d(LOG_PREFIX, "Creating Shell Surface");
        client.addResource(ssurface);
    }
}
