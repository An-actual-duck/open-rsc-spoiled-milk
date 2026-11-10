/** A separate loopback fixture only; ordinary/private-owner sessions stay unchanged. */
export function repeatableFixturePolicy(mode,options) {
  const requested=options.get('fixture');
  if(requested===null)return {properties:[],requested:null,tcpPort:43629,websocket:'ws://127.0.0.1:43529/'};
  if(mode!=='webgl'||!options.has('benchmark')||requested!=='repeatable-v1')throw new Error('Repeatable fixture requires the explicit private WebGL benchmark');
  return {properties:['spoiledmilk.browserRepeatableFixture=repeatable-v1'],requested,tcpPort:43649,websocket:'ws://127.0.0.1:43549/'};
}
