/** A separate loopback fixture only; ordinary/private-owner sessions stay unchanged. */
export function repeatableFixturePolicy(mode,options,privateRoute,origin) {
  const requested=options.get('fixture');
  if(privateRoute!==undefined){if(typeof origin!=='string'||!/^http:\/\/127\.0\.0\.1:\d{1,5}$/.test(origin)||!privateRoute||![[43639,'ws://127.0.0.1:43539/'],[43649,'ws://127.0.0.1:43549/'],[43659,'ws://127.0.0.1:43559/']].some(([tcp,ws])=>privateRoute.tcpPort===tcp&&privateRoute.websocket===ws))throw new Error('Invalid staged private loopback route');}
  if(privateRoute?.tcpPort===43659&&(requested!=='staged-login-v1'||options.get('stagedlogin')!=='on'))throw new Error('Staged-login route requires its explicit private fixture');
  if(requested===null){
    if(privateRoute!==undefined)return {properties:['spoiledmilk.browserPrivateLoopbackPort='+privateRoute.tcpPort],requested:null,tcpPort:privateRoute.tcpPort,websocket:privateRoute.websocket};
    return {properties:[],requested:null,tcpPort:43629,websocket:'ws://127.0.0.1:43529/'};
  }
  if(mode!=='webgl'||!options.has('benchmark')||!['repeatable-v1','renderer-combat-v1','staged-login-v1'].includes(requested))throw new Error('Repeatable fixture requires the explicit private WebGL benchmark');
  const combat=requested==='renderer-combat-v1',staged=requested==='staged-login-v1';
  if(staged&&options.get('stagedlogin')!=='on')throw new Error('Staged-login fixture requires staged login');
  if(privateRoute!==undefined&&privateRoute.tcpPort!==(staged?43659:combat?43639:43649))throw new Error('Staged private loopback route differs from fixture');
  return {properties:['spoiledmilk.browserRepeatableFixture='+requested,...(privateRoute===undefined?[]:['spoiledmilk.browserPrivateLoopbackPort='+privateRoute.tcpPort])],requested,tcpPort:staged?43659:combat?43639:43649,websocket:staged?'ws://127.0.0.1:43559/':combat?'ws://127.0.0.1:43539/':'ws://127.0.0.1:43549/'};
}
