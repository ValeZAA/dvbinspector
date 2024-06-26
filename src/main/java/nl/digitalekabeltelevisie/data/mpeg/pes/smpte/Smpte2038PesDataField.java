package nl.digitalekabeltelevisie.data.mpeg.pes.smpte;

import nl.digitalekabeltelevisie.data.mpeg.PesPacketData;
import nl.digitalekabeltelevisie.util.BitSource;
import nl.digitalekabeltelevisie.util.Utils;
import nl.digitalekabeltelevisie.controller.KVP;

import java.util.ArrayList;
import java.util.List;

import javax.swing.tree.DefaultMutableTreeNode;

public class Smpte2038PesDataField extends PesPacketData {

	final private List<AncillaryDataPacket> ancDataPackets = new ArrayList<AncillaryDataPacket>();

	/**
	 * Constructor for SMPTE2038 Packet
	 * 
	 * @param pesPacket
	 * @throws Exception
	 */
	protected Smpte2038PesDataField(final PesPacketData pesPacket) {
		super(pesPacket);

		BitSource bs = new BitSource(data, pesDataStart, pesDataStart + pesDataLen);

		while (bs.available() >= 64) {
			final AncillaryDataPacket ancData = createAncillaryDataPacket(bs);
			ancDataPackets.add(ancData);
			if (!bs.isByteAligned()) {
				bs.skiptoByteBoundary();
			}
		}
	}

	/**
	 * Handle display of SMPTE 2038 Pes Packet and create node with Ancillary data
	 */
	public DefaultMutableTreeNode getJTreeNode(final int modus) {
		final DefaultMutableTreeNode s = super.getJTreeNode(modus, new KVP("SMPTE 2038 PES Packet"));
		Utils.addListJTree(s, ancDataPackets, modus, "Ancillary Data");
		return s;
	}

	/**
	 * Create an ancillary data packet with given data
	 * 
	 * @param data  : data to read to create the packet
	 * @param start : offset indicating where to begin the parsing
	 * @return
	 * @throws Exception
	 */
	private AncillaryDataPacket createAncillaryDataPacket(final BitSource bs) {
		return new AncillaryDataPacket(bs);
	}
}
