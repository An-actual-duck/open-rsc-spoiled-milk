/** A separate loopback fixture only; ordinary/private-owner sessions stay unchanged. */
export function repeatableFixturePolicy(mode,options) {
  const requested=options.get('fixture');
  if(requested===null)return {properties:[],requested:null,tcpPort:43629,websocket:'ws://127.0.0.1:43529/'};
  if(mode!=='webgl'||!options.has('benchmark')||!['repeatable-v1','renderer-combat-v1'].includes(requested))throw new Error('Repeatable fixture requires the explicit private WebGL benchmark');
  const combat=requested==='renderer-combat-v1';
  return {properties:['spoiledmilk.browserRepeatableFixture='+requested],requested,tcpPort:combat?43639:43649,websocket:combat?'ws://127.0.0.1:43539/':'ws://127.0.0.1:43549/'};
}
