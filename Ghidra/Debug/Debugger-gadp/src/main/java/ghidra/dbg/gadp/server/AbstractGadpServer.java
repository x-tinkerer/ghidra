/* ###
 * IP: GHIDRA
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
package ghidra.dbg.gadp.server;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.CompletableFuture;

import ghidra.comm.service.AbstractAsyncServer;
import ghidra.dbg.*;
import ghidra.dbg.gadp.error.GadpErrorException;
import ghidra.dbg.gadp.protocol.Gadp;
import ghidra.program.model.address.*;
import ghidra.util.Msg;

@Deprecated(forRemoval = true, since = "11.2")
public abstract class AbstractGadpServer
		extends AbstractAsyncServer<AbstractGadpServer, GadpClientHandler>
		implements DebuggerModelListener {
	public static final String LISTENING_ON = "GADP Server listening on ";
	public static final String READY = "GADP Server model ready";

	protected final DebuggerObjectModel model;
	private boolean exitOnClosed = true;

	public AbstractGadpServer(DebuggerObjectModel model, SocketAddress addr) throws IOException {
		super(addr);
		this.model = model;
		System.out.println(LISTENING_ON + getLocalAddress());

		model.addModelListener(this);
	}

	@Override
	public CompletableFuture<Void> launchAsyncService() {
		System.out.println(READY);
		return super.launchAsyncService();
	}

	public DebuggerObjectModel getModel() {
		return model;
	}

	@Override
	protected boolean checkAcceptable(AsynchronousSocketChannel sock) {
		return true;
	}

	@Override
	protected GadpClientHandler newHandler(AsynchronousSocketChannel sock) {
		try {
			// Accept only the first connection
			closeServerSocket();
		}
		catch (IOException e) {
			Msg.error(this, "Could not close server socket", e);
		}
		return new GadpClientHandler(this, sock);
	}

	@Override
	protected void removeHandler(GadpClientHandler handler) {
		super.removeHandler(handler);
		try {
			terminate();
		}
		catch (IOException e) {
			Msg.error(this, "Could not terminate upon disconnect");
		}
	}

	protected AddressRange getAddressRange(Gadp.AddressRange range) {
		AddressSpace space = model.getAddressSpace(range.getSpace());
		if (space == null) {
			throw new GadpErrorException(Gadp.ErrorCode.EC_BAD_ADDRESS,
				"Unrecognized address space: " + range);
		}
		Address min = space.getAddress(range.getOffset());
		// TODO: Should extend be a long?
		// Note, +1 accounted for in how Ghidra AddressRanges work (inclusive of end)
		return new AddressRangeImpl(min, min.add(Integer.toUnsignedLong(range.getExtend())));
	}

	@Override
	public void modelClosed(DebuggerModelClosedReason reason) {
		if (exitOnClosed) {
			System.exit(0);
		}
	}

	/**
	 * By default, the GADP server will terminate the VM when the model is closed
	 * 
	 * <p>
	 * For testing purposes, it may be useful to disable this action.
	 * 
	 * @param exitOnClosed true to terminate the VM on close, false to remain running
	 */
	public void setExitOnClosed(boolean exitOnClosed) {
		this.exitOnClosed = exitOnClosed;
	}

	@Override
	public void terminate() throws IOException {
		super.terminate();
		model.close().exceptionally(ex -> {
			Msg.error(this, "Problem closing GADP-served model", ex);
			return null;
		});
	}
}
