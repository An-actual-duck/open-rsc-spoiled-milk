/** A separate loopback fixture only; ordinary/private-owner sessions stay unchanged. */
export function repeatableFixturePolicy(mode,options,privateRoute,origin) {
  const requested=options.get('fixture');
  if(privateRoute!==undefined){if(typeof origin!=='string'||!/^http:\/\/127\.0\.0\.1:\d{1,5}$/.test(origin)||!privateRoute||![[43639,'ws://127.0.0.1:43539/'],[43649,'ws://127.0.0.1:43549/']].some(([tcp,ws])=>privateRoute.tcpPort===tcp&&privateRoute.websocket===ws))throw new Error('Invalid staged private loopback route');}
  if(requested===null){
    if(privateRoute!==undefined)return {properties:[],requested:null,tcpPort:privateRoute.tcpPort,websocket:privateRoute.websocket};
    return {properties:[],requested:null,tcpPort:43629,websocket:'ws://127.0.0.1:43529/'};
  }
  if(mode!=='webgl'||!options.has('benchmark')||!['repeatable-v1','renderer-combat-v1'].includes(requested))throw new Error('Repeatable fixture requires the explicit private WebGL benchmark');
  const combat=requested==='renderer-combat-v1';
  if(privateRoute!==undefined&&privateRoute.tcpPort!==(combat?43639:43649))throw new Error('Staged private loopback route differs from fixture');
  return {properties:['spoiledmilk.browserRepeatableFixture='+requested],requested,tcpPort:combat?43639:43649,websocket:combat?'ws://127.0.0.1:43539/':'ws://127.0.0.1:43549/'};
}
