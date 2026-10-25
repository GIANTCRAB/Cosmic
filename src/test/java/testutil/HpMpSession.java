package testutil;

import client.Character;
import client.Client;
import client.Job;
import client.Stat;
import client.inventory.InventoryType;
import client.inventory.Item;
import config.YamlConfig;
import constants.id.ItemId;
import constants.id.MapId;
import io.netty.channel.ChannelHandlerContext;
import net.PacketProcessor;
import net.packet.Packet;
import net.server.PlayerStorage;
import net.server.Server;
import net.server.channel.Channel;
import net.server.channel.handlers.UseCashItemHandler;
import net.server.world.World;
import org.mockito.MockedStatic;
import server.ItemInformationProvider;
import server.maps.GenericPortal;
import server.maps.MapManager;
import server.maps.MapleMap;
import service.NoteService;

import java.awt.Point;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Real character/client state with virtual time and packet transport, without a running server. */
public final class HpMpSession implements AutoCloseable {
    public final AtomicLong time = new AtomicLong(10_000);
    public final Server server = mock(Server.class);
    public final World world = mock(World.class);
    public final Channel channel = mock(Channel.class);
    public final MapManager maps = mock(MapManager.class);
    public final PlayerStorage worldPlayers = new PlayerStorage();
    public final PlayerStorage channelPlayers = new PlayerStorage();
    public final Client client;
    public final Character chr;
    public final MapleMap map;
    public Item resets;

    private final UseCashItemHandler handler = new UseCashItemHandler(mock(NoteService.class));
    private final Map<Client, List<byte[]>> outbound = new IdentityHashMap<>();
    private final MockedStatic<Server> servers;
    private final MockedStatic<ItemInformationProvider> items;
    private final boolean previousRatio = YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE;
    private final boolean previousRandomize = YamlConfig.config.server.USE_RANDOMIZE_HPMP_GAIN;
    private final boolean previousEnforceSwap = YamlConfig.config.server.USE_ENFORCE_HPMP_SWAP;
    private final boolean previousAutosave = YamlConfig.config.server.USE_AUTOSAVE;

    public HpMpSession() {
        ItemInformationProvider provider = ItemInformationProviderMocks.mockProvider();
        servers = mockStatic(Server.class);
        items = mockStatic(ItemInformationProvider.class);
        servers.when(Server::getInstance).thenReturn(server);
        items.when(ItemInformationProvider::getInstance).thenReturn(provider);
        when(server.getCurrentTime()).thenAnswer(invocation -> time.get());
        when(server.isOnline()).thenReturn(true);
        when(server.getWorld(0)).thenReturn(world);
        when(server.getChannel(0, 1)).thenReturn(channel);
        when(world.getChannel(1)).thenReturn(channel);
        when(world.getPlayerStorage()).thenReturn(worldPlayers);
        when(channel.getPlayerStorage()).thenReturn(channelPlayers);
        when(channel.getMapFactory()).thenReturn(maps);

        YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE = false;
        YamlConfig.config.server.USE_RANDOMIZE_HPMP_GAIN = false;
        YamlConfig.config.server.USE_ENFORCE_HPMP_SWAP = false;
        YamlConfig.config.server.USE_AUTOSAVE = true;

        map = new MapleMap(100000000, 0, 1, MapId.NONE, 1);
        GenericPortal spawn = new GenericPortal(0);
        spawn.setId(0);
        spawn.setName("sp");
        spawn.setPosition(new Point());
        spawn.setTargetMapId(MapId.NONE);
        map.addPortal(spawn);
        when(maps.getMap(anyInt())).thenReturn(map);

        client = newClient(1);
        chr = Character.getDefault(client);
        chr.setName("Washer");
        chr.setJob(Job.BOWMAN);
        chr.setLevel(120);
        chr.setMap(map);
        client.setPlayer(chr);
        worldPlayers.addPlayer(chr);
        channelPlayers.addPlayer(chr);
    }

    public Client newClient(int channelId) {
        Client result = Client.createChannelClient(outbound.size() + 1L, "127.0.0.1",
                mock(PacketProcessor.class), 0, channelId);
        result.setAccID(1);
        result.setAccountName("hpmp-test");
        List<byte[]> packets = new ArrayList<>();
        outbound.put(result, packets);
        io.netty.channel.Channel transport = mock(io.netty.channel.Channel.class);
        when(transport.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        when(transport.writeAndFlush(any())).thenAnswer(invocation -> {
            Object message = invocation.getArgument(0);
            if (message instanceof Packet packet) {
                packets.add(packet.getBytes().clone());
            }
            return null;
        });
        ChannelHandlerContext context = mock(ChannelHandlerContext.class);
        when(context.channel()).thenReturn(transport);
        result.channelActive(context);
        return result;
    }

    public List<byte[]> packets(Client target) {
        return outbound.get(target);
    }

    public void preparePools(int hp, int mp, int resetQuantity) {
        boolean ratio = YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE;
        YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE = false;
        try {
            if (chr.getHpMpApUsed() == 0) {
                chr.gainAp(1, true);
                if (!chr.assignHP(1, 1)) {
                    throw new AssertionError("Could not establish the assigned HP/MP point");
                }
            }
            chr.updateMaxHpMaxMp(hp, mp);
            chr.healHpMp();
            resets = new Item(ItemId.AP_RESET, (short) 1, (short) resetQuantity);
            chr.getInventory(InventoryType.CASH).addItemFromDB(resets);
            packets(client).clear();
        } finally {
            YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE = ratio;
        }
    }

    public void useReset(Stat from, Stat to) {
        time.addAndGet(4_000);
        useResetNow(from.getValue(), to.getValue());
    }

    public void useResetNow(int from, int to) {
        handler.handlePacket(Packets.buildInPacket(p -> {
            p.writeShort(1);
            p.writeInt(ItemId.AP_RESET);
            p.writeInt(to);
            p.writeInt(from);
        }), client);
    }

    @Override
    public void close() {
        items.close();
        servers.close();
        YamlConfig.config.server.USE_FIXED_RATIO_HPMP_UPDATE = previousRatio;
        YamlConfig.config.server.USE_RANDOMIZE_HPMP_GAIN = previousRandomize;
        YamlConfig.config.server.USE_ENFORCE_HPMP_SWAP = previousEnforceSwap;
        YamlConfig.config.server.USE_AUTOSAVE = previousAutosave;
    }
}
