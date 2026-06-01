<script lang="ts">
	import { onMount } from 'svelte';
	import { fade, fly, slide } from 'svelte/transition';

	interface Tunnel {
		subdomain: string;
		bytes_sent: number;
		bytes_recv: number;
	}

	interface CapturedRequest {
		id: string;
		tunnel_id: string;
		timestamp: string;
		method: string;
		path: string;
		host: string;
		headers: string[][];
	}

	interface Metrics {
		total_tunnels: number;
		tunnels: Tunnel[];
		request_history: CapturedRequest[];
	}

	let metrics: Metrics = { total_tunnels: 0, tunnels: [], request_history: [] };
	let connected = false;
	let lastUpdate = new Date();
	let selectedRequest: CapturedRequest | null = null;
	let activeTab: 'overview' | 'traffic' = 'overview';

	function formatBytes(bytes: number) {
		if (bytes === 0) return '0 B';
		const k = 1024;
		const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
		const i = Math.floor(Math.log(bytes) / Math.log(k));
		return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
	}

	function formatTime(timestamp: string) {
		return new Date(timestamp).toLocaleTimeString();
	}

	onMount(() => {
		const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
		const ws = new WebSocket(`${protocol}//${window.location.hostname}:4000/ws`);

		ws.onopen = () => {
			connected = true;
			console.log('Connected to D-RAP API');
		};

		ws.onmessage = (event) => {
			metrics = JSON.parse(event.data);
			lastUpdate = new Date();
		};

		ws.onclose = () => {
			connected = false;
			console.log('Disconnected from D-RAP API');
		};

		return () => ws.close();
	});

	async fn replayRequest(id: string) {
		const res = await fetch(`http://${window.location.hostname}:4000/api/replay/${id}`, {
			method: 'POST'
		});
		if (res.ok) {
			console.log('Replay triggered');
		} else {
			console.error('Replay failed');
		}
	}
</script>

<svelte:head>
	<title>D-RAP | Command Center</title>
</svelte:head>

<div class="min-h-screen bg-[#0a0a0c] text-slate-100 font-inter selection:bg-cyan-500/30">
	<!-- Background Glows -->
	<div class="fixed top-0 left-0 w-full h-full overflow-hidden pointer-events-none -z-10 text-cyan-400">
		<div class="absolute top-[-10%] left-[-10%] w-[40%] h-[40%] bg-current opacity-5 blur-[120px] rounded-full"></div>
		<div class="absolute bottom-[-10%] right-[-10%] w-[40%] h-[40%] bg-purple-500/5 blur-[120px] rounded-full"></div>
	</div>

	<!-- Sidebar -->
	<aside class="fixed left-0 top-0 h-full w-64 bg-slate-900/40 backdrop-blur-xl border-r border-slate-800/50 hidden lg:flex flex-col">
		<div class="p-8">
			<div class="flex items-center gap-3">
				<div class="w-8 h-8 bg-gradient-to-br from-cyan-400 to-blue-600 rounded-lg flex items-center justify-center shadow-lg shadow-cyan-500/20">
					<span class="font-orbitron font-black text-white text-xs">D</span>
				</div>
				<h1 class="font-orbitron font-bold text-xl tracking-tighter bg-clip-text text-transparent bg-gradient-to-r from-white to-slate-400">
					D-RAP
				</h1>
			</div>
			<div class="mt-2 text-[10px] font-orbitron text-slate-500 tracking-[0.2em] uppercase">
				EmpireBot Control
			</div>
		</div>

		<nav class="flex-1 px-4 space-y-1">
			<button 
				on:click={() => activeTab = 'overview'}
				class="w-full flex items-center gap-3 px-4 py-3 {activeTab === 'overview' ? 'bg-cyan-500/10 text-cyan-400 border-cyan-500/20' : 'text-slate-400 hover:text-white hover:bg-white/5 border-transparent'} rounded-xl border transition-all">
				<svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/></svg>
				<span class="font-medium text-sm">Overview</span>
			</button>
			<button 
				on:click={() => activeTab = 'traffic'}
				class="w-full flex items-center gap-3 px-4 py-3 {activeTab === 'traffic' ? 'bg-cyan-500/10 text-cyan-400 border-cyan-500/20' : 'text-slate-400 hover:text-white hover:bg-white/5 border-transparent'} rounded-xl border transition-all">
				<svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polyline points="22 12 18 12 15 21 9 3 6 12 2 12"/></svg>
				<span class="font-medium text-sm">Traffic Log</span>
			</button>
		</nav>

		<div class="p-6 border-t border-slate-800/50">
			<div class="flex items-center gap-3 px-4 py-3 bg-slate-800/50 rounded-xl">
				<div class="w-2 h-2 rounded-full {connected ? 'bg-green-500 animate-pulse' : 'bg-red-500'}"></div>
				<span class="text-xs font-medium text-slate-300">
					{connected ? 'Relay Online' : 'Relay Offline'}
				</span>
			</div>
		</div>
	</aside>

	<!-- Main Content -->
	<main class="lg:pl-64 p-6 lg:p-12 max-w-7xl mx-auto">
		<!-- Header -->
		<header class="mb-12 flex flex-col md:flex-row md:items-end justify-between gap-6">
			<div>
				<h2 class="text-3xl font-orbitron font-black text-white tracking-tight mb-2 uppercase">
					{activeTab === 'overview' ? 'Command Center' : 'Network Traffic'}
				</h2>
				<p class="text-slate-400 text-sm">Monitoring real-time tunnel infrastructure across EmpireBot Relay.</p>
			</div>
			<div class="text-right">
				<div class="text-[10px] font-orbitron text-slate-500 uppercase tracking-widest mb-1">System Time</div>
				<div class="text-xl font-orbitron font-medium text-white tracking-widest">{lastUpdate.toLocaleTimeString()}</div>
			</div>
		</header>

		{#if activeTab === 'overview'}
			<!-- Stats Grid -->
			<section in:fade class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-12">
				<div class="bg-slate-900/40 backdrop-blur-md border border-slate-800/50 p-6 rounded-2xl hover:border-cyan-500/30 transition-all group">
					<div class="flex items-center justify-between mb-4">
						<div class="p-2 bg-cyan-500/10 rounded-lg text-cyan-400 group-hover:scale-110 transition-transform">
							<svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.28 1.15-.28 2.35 0 3.5-.73 1.02-1.08 2.25-1 3.5 0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4"/></svg>
						</div>
						<div class="text-xs font-orbitron text-slate-500">Live</div>
					</div>
					<div class="text-2xl font-orbitron font-bold text-white mb-1 tracking-tight">{metrics.total_tunnels}</div>
					<div class="text-xs text-slate-400 font-medium">Active Tunnels</div>
				</div>

				<div class="bg-slate-900/40 backdrop-blur-md border border-slate-800/50 p-6 rounded-2xl hover:border-purple-500/30 transition-all group">
					<div class="flex items-center justify-between mb-4">
						<div class="p-2 bg-purple-500/10 rounded-lg text-purple-400 group-hover:scale-110 transition-transform">
							<svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="7 10 12 15 17 10"/><line x1="12" y1="15" x2="12" y2="3"/></svg>
						</div>
						<div class="text-xs font-orbitron text-slate-500">Incoming</div>
					</div>
					<div class="text-2xl font-orbitron font-bold text-white mb-1 tracking-tight">
						{formatBytes(metrics.tunnels.reduce((acc, t) => acc + (t.bytes_recv || 0), 0))}
					</div>
					<div class="text-xs text-slate-400 font-medium">Total Ingress</div>
				</div>

				<div class="bg-slate-900/40 backdrop-blur-md border border-slate-800/50 p-6 rounded-2xl hover:border-amber-500/30 transition-all group">
					<div class="flex items-center justify-between mb-4">
						<div class="p-2 bg-amber-500/10 rounded-lg text-amber-400 group-hover:scale-110 transition-transform">
							<svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
						</div>
						<div class="text-xs font-orbitron text-slate-500">Outgoing</div>
					</div>
					<div class="text-2xl font-orbitron font-bold text-white mb-1 tracking-tight">
						{formatBytes(metrics.tunnels.reduce((acc, t) => acc + (t.bytes_sent || 0), 0))}
					</div>
					<div class="text-xs text-slate-400 font-medium">Total Egress</div>
				</div>

				<div class="bg-slate-900/40 backdrop-blur-md border border-slate-800/50 p-6 rounded-2xl hover:border-green-500/30 transition-all group">
					<div class="flex items-center justify-between mb-4">
						<div class="p-2 bg-green-500/10 rounded-lg text-green-400 group-hover:scale-110 transition-transform">
							<svg xmlns="http://www.w3.org/2000/svg" class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><polyline points="12 6 12 12 16 14"/></svg>
						</div>
						<div class="text-xs font-orbitron text-slate-500">Status</div>
					</div>
					<div class="text-2xl font-orbitron font-bold text-white mb-1 tracking-tight">99.9%</div>
					<div class="text-xs text-slate-400 font-medium">Server Uptime</div>
				</div>
			</section>

			<!-- Tunnels Table -->
			<section in:fade class="bg-slate-900/40 backdrop-blur-md border border-slate-800/50 rounded-3xl overflow-hidden shadow-2xl shadow-black/50">
				<div class="px-8 py-6 border-b border-slate-800/50 flex items-center justify-between">
					<h3 class="font-orbitron font-bold text-lg text-white">Active Traffic Streams</h3>
				</div>
				
				<div class="overflow-x-auto">
					<table class="w-full text-left">
						<thead class="bg-black/20 text-slate-500 text-[10px] font-orbitron uppercase tracking-widest">
							<tr>
								<th class="px-8 py-4 border-b border-slate-800/30">Tunnel Endpoint</th>
								<th class="px-8 py-4 border-b border-slate-800/30">Status</th>
								<th class="px-8 py-4 border-b border-slate-800/30">Ingress</th>
								<th class="px-8 py-4 border-b border-slate-800/30">Egress</th>
								<th class="px-8 py-4 text-right border-b border-slate-800/30">Protocol</th>
							</tr>
						</thead>
						<tbody class="divide-y divide-slate-800/30">
							{#each metrics.tunnels as tunnel}
								<tr class="hover:bg-white/[0.02] transition-colors group">
									<td class="px-8 py-6">
										<div class="flex flex-col">
											<span class="text-sm font-semibold text-white group-hover:text-cyan-400 transition-colors">
												{tunnel.subdomain}.empirebot.in
											</span>
											<span class="text-[10px] text-slate-500 font-mono mt-0.5 uppercase tracking-tighter">
												ID: {tunnel.subdomain}
											</span>
										</div>
									</td>
									<td class="px-8 py-6">
										<span class="inline-flex items-center gap-1.5 px-2 py-1 rounded-full bg-green-500/10 text-green-400 text-[10px] font-bold uppercase tracking-tight">
											<span class="w-1.5 h-1.5 rounded-full bg-green-500"></span>
											Connected
										</span>
									</td>
									<td class="px-8 py-6">
										<span class="text-xs font-medium text-slate-300 font-mono">
											{formatBytes(tunnel.bytes_recv || 0)}
										</span>
									</td>
									<td class="px-8 py-6">
										<span class="text-xs font-medium text-slate-300 font-mono">
											{formatBytes(tunnel.bytes_sent || 0)}
										</span>
									</td>
									<td class="px-8 py-6 text-right">
										<span class="text-[10px] font-bold text-slate-500 border border-slate-800 px-2 py-1 rounded uppercase">HTTP/1.1</span>
									</td>
								</tr>
							{:else}
								<tr>
									<td colspan="5" class="px-8 py-20 text-center">
										<p class="text-slate-500 text-sm">No active tunnels detected.</p>
									</td>
								</tr>
							{/each}
						</tbody>
					</table>
				</div>
			</section>
		{:else}
			<!-- Traffic Log Tab -->
			<div in:fade class="grid grid-cols-1 lg:grid-cols-3 gap-8">
				<!-- List -->
				<div class="lg:col-span-2 bg-slate-900/40 backdrop-blur-md border border-slate-800/50 rounded-3xl overflow-hidden flex flex-col h-[700px]">
					<div class="px-8 py-6 border-b border-slate-800/50">
						<h3 class="font-orbitron font-bold text-lg text-white">Live Traffic Sniffer</h3>
					</div>
					<div class="flex-1 overflow-y-auto custom-scrollbar">
						<table class="w-full text-left border-collapse">
							<thead class="sticky top-0 bg-slate-900/90 backdrop-blur-md text-slate-500 text-[10px] font-orbitron uppercase tracking-widest z-10">
								<tr>
									<th class="px-8 py-4 border-b border-slate-800/50">Timestamp</th>
									<th class="px-8 py-4 border-b border-slate-800/50">Method</th>
									<th class="px-8 py-4 border-b border-slate-800/50">Tunnel</th>
									<th class="px-8 py-4 border-b border-slate-800/50">Path</th>
								</tr>
							</thead>
							<tbody class="divide-y divide-slate-800/30">
								{#each [...metrics.request_history].reverse() as req (req.id)}
									<tr 
										on:click={() => selectedRequest = req}
										class="hover:bg-cyan-500/5 transition-all cursor-pointer {selectedRequest?.id === req.id ? 'bg-cyan-500/10' : ''}">
										<td class="px-8 py-4 text-xs text-slate-500 font-mono">
											{formatTime(req.timestamp)}
										</td>
										<td class="px-8 py-4">
											<span class="px-2 py-1 rounded text-[10px] font-black uppercase tracking-widest {req.method === 'GET' ? 'bg-green-500/10 text-green-400' : 'bg-blue-500/10 text-blue-400'}">
												{req.method}
											</span>
										</td>
										<td class="px-8 py-4 text-xs font-semibold text-slate-300">
											{req.tunnel_id}
										</td>
										<td class="px-8 py-4 text-xs text-slate-400 truncate max-w-[200px]">
											{req.path}
										</td>
									</tr>
								{/each}
							</tbody>
						</table>
					</div>
				</div>

				<!-- Inspector -->
				<div class="bg-slate-900/40 backdrop-blur-md border border-slate-800/50 rounded-3xl overflow-hidden h-[700px] flex flex-col">
					<div class="px-8 py-6 border-b border-slate-800/50">
						<h3 class="font-orbitron font-bold text-lg text-white">Inspector</h3>
					</div>
					<div class="flex-1 overflow-y-auto p-8 custom-scrollbar">
						{#if selectedRequest}
							<div in:slide>
								<div class="mb-8">
									<div class="text-[10px] font-orbitron text-slate-500 uppercase tracking-widest mb-2">Request Origin</div>
									<div class="text-xl font-bold text-white mb-1">{selectedRequest.method}</div>
									<div class="text-sm text-cyan-400 font-mono break-all leading-relaxed">
										{selectedRequest.host}{selectedRequest.path}
									</div>
								</div>

								<div class="space-y-6">
									<div>
										<div class="text-[10px] font-orbitron text-slate-500 uppercase tracking-widest mb-3">HTTP Headers</div>
										<div class="bg-black/40 rounded-2xl border border-slate-800/50 divide-y divide-slate-800/30 overflow-hidden">
											{#each selectedRequest.headers as [key, value]}
												<div class="px-4 py-3 flex flex-col gap-1">
													<span class="text-[10px] font-bold text-slate-500 uppercase tracking-tight">{key}</span>
													<span class="text-xs text-slate-300 break-all font-mono leading-relaxed">{value}</span>
												</div>
											{/each}
										</div>
									</div>

									<button 
										on:click={() => replayRequest(selectedRequest.id)}
										class="w-full py-4 bg-gradient-to-r from-cyan-500 to-blue-600 hover:from-cyan-400 hover:to-blue-500 text-white rounded-2xl font-orbitron font-bold text-sm shadow-lg shadow-cyan-900/20 transition-all active:scale-95">
										REPLAY REQUEST
									</button>
								</div>
							</div>
						{:else}
							<div class="h-full flex flex-col items-center justify-center text-center opacity-40">
								<svg xmlns="http://www.w3.org/2000/svg" class="w-12 h-12 mb-4" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="10"/><line x1="12" y1="8" x2="12" y2="12"/><line x1="12" y1="16" x2="12.01" y2="16"/></svg>
								<p class="text-sm font-orbitron uppercase tracking-widest">Select a session<br/>to inspect</p>
							</div>
						{/if}
					</div>
				</div>
			</div>
		{/if}
	</main>
</div>

<style lang="postcss">
	:global(body) {
		@apply bg-[#0a0a0c];
		overflow-x: hidden;
	}

	.font-inter {
		font-family: 'Inter', sans-serif;
	}

	.font-orbitron {
		font-family: 'Orbitron', sans-serif;
	}

	.custom-scrollbar::-webkit-scrollbar {
		width: 4px;
	}
	.custom-scrollbar::-webkit-scrollbar-track {
		background: transparent;
	}
	.custom-scrollbar::-webkit-scrollbar-thumb {
		@apply bg-slate-800 rounded-full hover:bg-slate-700;
	}
</style>
