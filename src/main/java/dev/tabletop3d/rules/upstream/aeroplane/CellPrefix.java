// Upstream MIT: kan01234/aeroplanes-chess. See META-INF/licenses.
package dev.tabletop3d.rules.upstream.aeroplane;

public enum CellPrefix {

	BASE("ba"), TAKEOFF("to"), SKY("sk"), LANDING("ld"), GOAL("go");

	private String prefix;

	private CellPrefix(String prefix) {
		this.prefix = prefix;
	}

	public String getPrefix() {
		return prefix;
	}

}
