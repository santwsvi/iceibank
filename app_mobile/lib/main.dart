import 'dart:async';
import 'dart:convert';
import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

const agencies = ['http://localhost:4000', 'http://localhost:4001', 'http://localhost:4002'];

class LocalStore {
  final SharedPreferences prefs;
  LocalStore(this.prefs);
  List<Map<String,Object?>> _read(String table) => (jsonDecode(prefs.getString(table) ?? '[]') as List).cast<Map<String,Object?>>();
  Future<int> insert(String table, Map<String,Object?> row, {Object? conflictAlgorithm}) async {
    final rows=_read(table);
    if(table=='pending') row={...row,'id':DateTime.now().microsecondsSinceEpoch};
    if(table=='accounts') rows.removeWhere((r)=>r['id']==row['id']);
    rows.add(row); await prefs.setString(table,jsonEncode(rows)); return (row['id'] as num?)?.toInt() ?? rows.length;
  }
  Future<List<Map<String,Object?>>> query(String table,{String? where,List<Object?>? whereArgs,String? orderBy,int? limit}) async {
    var rows=_read(table);
    if(where=='conflict IS NULL') rows=rows.where((r)=>r['conflict']==null).toList();
    if(orderBy!=null) rows.sort((a,b)=>(a['id'] as num).compareTo(b['id'] as num));
    if(limit!=null && rows.length>limit) rows=rows.sublist(rows.length-limit);
    return rows;
  }
  Future<int> update(String table,Map<String,Object?> values,{String? where,List<Object?>? whereArgs}) async {
    final rows=_read(table); for(var i=0;i<rows.length;i++){if(rows[i]['id']==whereArgs?.first) rows[i]={...rows[i],...values};}
    await prefs.setString(table,jsonEncode(rows)); return 1;
  }
  Future<int> delete(String table,{String? where,List<Object?>? whereArgs}) async {
    final rows=_read(table)..removeWhere((r)=>r['id']==whereArgs?.first);
    await prefs.setString(table,jsonEncode(rows)); return 1;
  }
}

void main() => runApp(const IceiApp());
class IceiApp extends StatelessWidget {
  const IceiApp({super.key});
  @override Widget build(BuildContext context) => MaterialApp(
    title: 'ICEIBank', debugShowCheckedModeBanner: false,
    theme: ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xff176b53), brightness: Brightness.light),
      scaffoldBackgroundColor: const Color(0xfff4f7f4),
      appBarTheme: const AppBarTheme(backgroundColor: Color(0xfff4f7f4), foregroundColor: Color(0xff172b27), elevation: 0),
      cardTheme: CardThemeData(color: Colors.white, elevation: 0, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20), side: const BorderSide(color: Color(0xffe2e9e5)))),
      inputDecorationTheme: InputDecorationTheme(filled: true, fillColor: const Color(0xfffbfcfb), contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 15), border: OutlineInputBorder(borderRadius: BorderRadius.circular(13), borderSide: const BorderSide(color: Color(0xffd7e1db))), enabledBorder: OutlineInputBorder(borderRadius: BorderRadius.circular(13), borderSide: const BorderSide(color: Color(0xffd7e1db)))),
      filledButtonTheme: FilledButtonThemeData(style: FilledButton.styleFrom(minimumSize: const Size(0, 50), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(13)), textStyle: const TextStyle(fontWeight: FontWeight.w700))),
    ), home: const BankPage());
}

class BankPage extends StatefulWidget { const BankPage({super.key}); @override State<BankPage> createState() => _BankPageState(); }
class _BankPageState extends State<BankPage> {
  LocalStore? db; String? token, message; int base = 0; int? leader; int? accountId; double? balance; List<int> vector = [0,0,0]; List<Map<String,Object?>> pending = [];
  final user = TextEditingController(text: 'aluno'), pass = TextEditingController(text: 'senha123'), account = TextEditingController(), amount = TextEditingController(), destination = TextEditingController(), transferAmount = TextEditingController();
  Timer? electionTimer; StreamSubscription<List<ConnectivityResult>>? connection;
  @override void initState() { super.initState(); _init(); }
  Future<void> _init() async { final prefs = await SharedPreferences.getInstance(); db = LocalStore(prefs); token = prefs.getString('token'); if (token != null) { await _loadCached(); electionTimer = Timer.periodic(const Duration(seconds: 4), (_) => _pollLeader()); connection = Connectivity().onConnectivityChanged.listen((s) { if (!s.contains(ConnectivityResult.none)) _sync(); }); await _pollLeader(); } if(mounted)setState((){}); }
  Future<void> _loadCached() async { final rows = await db!.query('accounts', orderBy:'id DESC', limit:1); if(rows.isNotEmpty){accountId=rows.first['id'] as int; balance=rows.first['balance'] as double; vector=(jsonDecode(rows.first['vector'] as String) as List).cast<int>();} await _refreshPending(); }
  Future<void> _refreshPending() async { pending = await db!.query('pending'); if(mounted)setState((){}); }
  Map<String,String> get headers => {'Authorization':'Bearer $token','Content-Type':'application/json'};
  Future<Map<String,dynamic>> _request(String method, String path, {Map<String,dynamic>? body, int? agency}) async { final uri=Uri.parse('${agencies[agency ?? base]}$path'); final response= method=='GET' ? await http.get(uri,headers:headers).timeout(const Duration(seconds:3)) : await http.post(uri,headers:headers,body:jsonEncode(body)).timeout(const Duration(seconds:3)); final data=jsonDecode(response.body) as Map<String,dynamic>; if(response.statusCode<200||response.statusCode>=300) throw Exception(data['erro'] ?? 'HTTP ${response.statusCode}'); return data; }
  Future<void> _login() async { try { final res=await http.post(Uri.parse('${agencies[base]}/auth/login'),headers:{'Content-Type':'application/json'},body:jsonEncode({'usuario':user.text,'senha':pass.text})); if(res.statusCode!=200) throw Exception('Credenciais inválidas'); token=jsonDecode(res.body)['token']; final prefs=await SharedPreferences.getInstance(); await prefs.setString('token',token!); electionTimer=Timer.periodic(const Duration(seconds:4),(_)=>_pollLeader()); connection=Connectivity().onConnectivityChanged.listen((s){if(!s.contains(ConnectivityResult.none))_sync();}); await _pollLeader(); setState((){message='Login realizado';}); } catch(e){setState(()=>message='$e');} }
  Future<void> _pollLeader() async { for(var i=0;i<agencies.length;i++){try{final result=await _request('GET','/bully/lider',agency:i); final got=result['liderAtual'] as int; if(got>=0&&mounted)setState(()=>leader=got); return;}catch(_){}} }
  Future<void> _getBalance() async { try {accountId=int.parse(account.text); final data=await _request('GET','/contas/$accountId'); balance=(data['saldo'] as num).toDouble(); vector=(data['vetorAtual'] as List).cast<int>(); await db!.insert('accounts',{'id':accountId,'name':data['nomeAluno'],'balance':balance,'vector':jsonEncode(vector)}); setState(()=>message='Saldo atualizado');}catch(e){await _loadCached();setState(()=>message='API indisponível; exibindo último saldo em cache. $e');} }
  Future<void> _move(String type) async { final id=accountId ?? int.tryParse(account.text); final value=double.tryParse(amount.text); if(id==null||value==null||value<=0){setState(()=>message='Informe conta e valor válidos');return;} accountId=id; try { final route=type=='DEPOSITO'?'depositar':'sacar'; final data=await _request('POST','/contas/$id/$route',body:{'valor':value}); balance=(data['saldo'] as num).toDouble(); vector=(await _request('GET','/contas/$id'))['vetorAtual'].cast<int>(); await _cache(); setState(()=>message='$type concluído'); } catch(e) { final online=await Connectivity().checkConnectivity(); if(!online.contains(ConnectivityResult.none)&&!e.toString().contains('SocketException')&&!e.toString().contains('TimeoutException')){setState(()=>message='$e');return;} await db!.insert('pending',{'account':id,'type':type,'amount':value,'vector':jsonEncode(vector),'conflict':null}); balance=(balance??0)+(type=='DEPOSITO'?value:-value); await _cache(); await _refreshPending(); setState(()=>message='Operação pendente; saldo provisório exibido.'); } }
  Future<void> _cache() async { if(accountId!=null)await db!.insert('accounts',{'id':accountId,'name':'Conta $accountId','balance':balance??0,'vector':jsonEncode(vector)}); }
  Future<void> _sync() async { if(db==null||token==null)return; final items=await db!.query('pending',where:'conflict IS NULL',orderBy:'id'); for(final item in items){try{final owner=(item['account'] as int)%3; final res=await http.post(Uri.parse('${agencies[owner]}/contas/${item['account']}/sincronizar'),headers:headers,body:jsonEncode({'tipo':item['type'],'valor':item['amount'],'vetorConhecido':jsonDecode(item['vector'] as String)})).timeout(const Duration(seconds:3)); final data=jsonDecode(res.body); if(res.statusCode==409){await db!.update('pending',{'conflict':jsonEncode(data)},where:'id=?',whereArgs:[item['id']]); balance=(data['contaAtual']['saldo'] as num).toDouble(); vector=(data['vetorAtual'] as List).cast<int>(); await _cache(); if(mounted)setState(()=>message='Conflito: operação pendente não aplicada. Saldo real atualizado; revise a pendência.');}else if(res.statusCode==200){await db!.delete('pending',where:'id=?',whereArgs:[item['id']]); balance=(data['saldo'] as num).toDouble(); vector=(data['vetorAtual'] as List).cast<int>(); await _cache(); if(mounted)setState(()=>message='Operação offline sincronizada automaticamente.');}else{throw Exception(data['erro']??'HTTP ${res.statusCode}');}}catch(e){if(mounted)setState(()=>message='Sincronização aguardando conexão: $e');break;}} await _refreshPending(); }
  Future<void> _transfer() async { try {final id=accountId??int.parse(account.text); final dest=int.parse(destination.text); final data=await _request('POST','/transferencias',body:{'idOrigem':id,'idDestino':dest,'valor':double.parse(transferAmount.text)}); setState(()=>message=data['mensagem']); await _getBalance();}catch(e){setState(()=>message='$e');} }
  @override void dispose(){electionTimer?.cancel();connection?.cancel();for(final c in [user,pass,account,amount,destination,transferAmount]){c.dispose();}super.dispose();}
  Widget _fieldLabel(String text) => Padding(padding: const EdgeInsets.only(bottom: 8), child: Text(text, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w700, color: Color(0xff52635c))));
  @override Widget build(BuildContext context) {
    final logged=token!=null;
    return Scaffold(appBar: logged ? AppBar(title: const Text('ICEIBank', style: TextStyle(fontWeight: FontWeight.w800, letterSpacing: -.4)), actions:[TextButton.icon(onPressed:(){setState(()=>token=null);},icon:const Icon(Icons.logout),label:const Text('Sair')),const SizedBox(width:12)]) : null,
      body: logged ? _dashboard() : _loginView());
  }
  Widget _loginView() => Center(child: SingleChildScrollView(padding: const EdgeInsets.all(24), child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 460), child: Card(child: Padding(padding: const EdgeInsets.all(28), child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children:[
    Container(width:48,height:48,alignment:Alignment.center,decoration:BoxDecoration(color:Theme.of(context).colorScheme.primary,borderRadius:BorderRadius.circular(16)),child:const Text('I',style:TextStyle(color:Colors.white,fontWeight:FontWeight.w900,fontSize:24))),
    const SizedBox(height:22), const Text('Bem-vinda de volta',style:TextStyle(fontSize:26,fontWeight:FontWeight.w800,letterSpacing:-.7)), const SizedBox(height:6), const Text('Entre para acompanhar sua conta e movimentações.',style:TextStyle(color:Color(0xff71807a))), const SizedBox(height:24),
    _fieldLabel('Agência de acesso'), DropdownButtonFormField<int>(value:base,decoration:const InputDecoration(),items:List.generate(3,(i)=>DropdownMenuItem(value:i,child:Text('Agência $i'))),onChanged:(v)=>setState(()=>base=v!)), const SizedBox(height:16),
    _fieldLabel('Usuário'), TextField(controller:user,decoration:const InputDecoration(hintText:'Seu usuário')), const SizedBox(height:16), _fieldLabel('Senha'), TextField(controller:pass,obscureText:true,decoration:const InputDecoration(hintText:'Sua senha')), const SizedBox(height:22),
    FilledButton(onPressed:_login,child:const Text('Entrar na minha conta')), if(message!=null)Padding(padding:const EdgeInsets.only(top:14),child:Text(message!,style:TextStyle(color:Theme.of(context).colorScheme.error))), const SizedBox(height:16), const Center(child:Text('Acesso protegido · ICEIBank',style:TextStyle(fontSize:12,color:Color(0xff71807a))))
  ]))))));
  Widget _dashboard() => LayoutBuilder(builder:(context,constraints){final wide=constraints.maxWidth>780;return Center(child:ConstrainedBox(constraints:const BoxConstraints(maxWidth:1120),child:ListView(padding:EdgeInsets.symmetric(horizontal:wide?32:18,vertical:24),children:[
    Text('Visão geral',style:Theme.of(context).textTheme.labelLarge?.copyWith(color:const Color(0xff71807a),letterSpacing:1.1)), const SizedBox(height:4), const Text('Seu dinheiro, com clareza.',style:TextStyle(fontSize:28,fontWeight:FontWeight.w800,letterSpacing:-.8)), const SizedBox(height:20),
    if(wide) Row(crossAxisAlignment:CrossAxisAlignment.start,children:[Expanded(flex:6,child:_balanceCard()),const SizedBox(width:16),Expanded(flex:4,child:_leaderCard())]) else ...[_balanceCard(),const SizedBox(height:12),_leaderCard()],
    const SizedBox(height:18), Card(child:Padding(padding:const EdgeInsets.all(20),child:Column(crossAxisAlignment:CrossAxisAlignment.stretch,children:[const Text('Consultar conta',style:TextStyle(fontWeight:FontWeight.w800,fontSize:17)),const SizedBox(height:5),const Text('Veja saldo atual ou último saldo salvo no dispositivo.',style:TextStyle(color:Color(0xff71807a),fontSize:13)),const SizedBox(height:16),Row(children:[Expanded(child:TextField(controller:account,keyboardType:TextInputType.number,decoration:const InputDecoration(labelText:'Número da conta',hintText:'Ex.: 600'))),const SizedBox(width:10),FilledButton(onPressed:_getBalance,child:const Text('Consultar'))])]))),
    const SizedBox(height:18), GridView.count(shrinkWrap:true,physics:const NeverScrollableScrollPhysics(),crossAxisCount:wide?2:1,crossAxisSpacing:14,mainAxisSpacing:14,childAspectRatio:wide?1.35:1.12,children:[_movementCard(),_transferCard()]),
    if(message!=null)Padding(padding:const EdgeInsets.only(top:14),child:Container(padding:const EdgeInsets.all(14),decoration:BoxDecoration(color:const Color(0xffedf6f0),borderRadius:BorderRadius.circular(14)),child:Text(message!))),
    if(pending.isNotEmpty)...[const SizedBox(height:18),Card(child:Padding(padding:const EdgeInsets.all(18),child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[Text('Operações pendentes · ${pending.length}',style:const TextStyle(fontWeight:FontWeight.w800)),const SizedBox(height:8),...pending.map((op)=>ListTile(contentPadding:EdgeInsets.zero,leading:Icon(op['conflict']==null?Icons.sync:Icons.warning_amber_rounded,color:op['conflict']==null?const Color(0xffb17a18):const Color(0xffb5473e)),title:Text('${op['type']}  R\$ ${op['amount']}'),subtitle:Text(op['conflict']==null?'Aguardando conexão para sincronizar':'Conflito detectado · operação precisa de revisão')))])))],
  ])));});
  Widget _balanceCard() => Container(padding:const EdgeInsets.all(22),decoration:BoxDecoration(color:const Color(0xff102b26),borderRadius:BorderRadius.circular(20)),child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[const Text('SALDO DISPONÍVEL',style:TextStyle(color:Color(0xffb7cbc0),fontSize:11,letterSpacing:1.1,fontWeight:FontWeight.w700)),const SizedBox(height:15),Text(balance==null?'R\$ —':'R\$ ${balance!.toStringAsFixed(2)}',style:const TextStyle(color:Colors.white,fontSize:34,fontWeight:FontWeight.w800,letterSpacing:-1)),const SizedBox(height:10),Text(accountId==null?'Consulte uma conta para carregar o saldo':'Conta $accountId · ${pending.isEmpty?'Saldo atualizado':'Saldo provisório'}',style:const TextStyle(color:Color(0xffc7d7ce),fontSize:13))]));
  Widget _leaderCard() => Card(child:Padding(padding:const EdgeInsets.all(20),child:Column(crossAxisAlignment:CrossAxisAlignment.start,mainAxisAlignment:MainAxisAlignment.center,children:[const Text('CONSENSO DA REDE',style:TextStyle(color:Color(0xff71807a),fontSize:11,letterSpacing:1,fontWeight:FontWeight.w700)),const SizedBox(height:14),Row(children:[Container(width:44,height:44,decoration:BoxDecoration(color:const Color(0xfffff6dc),borderRadius:BorderRadius.circular(14)),child:const Icon(Icons.military_tech,color:Color(0xff9a731a))),const SizedBox(width:12),Expanded(child:Column(crossAxisAlignment:CrossAxisAlignment.start,children:[Text(leader==null?'Buscando líder':'Agência $leader',style:const TextStyle(fontWeight:FontWeight.w800,fontSize:19)),const Text('Atualiza automaticamente',style:TextStyle(color:Color(0xff71807a),fontSize:12))])),Container(width:8,height:8,decoration:const BoxDecoration(color:Color(0xff51b77b),shape:BoxShape.circle))])])));
  Widget _movementCard() => Card(child:Padding(padding:const EdgeInsets.all(18),child:Column(crossAxisAlignment:CrossAxisAlignment.stretch,children:[const Text('Depositar ou sacar',style:TextStyle(fontSize:17,fontWeight:FontWeight.w800)),const SizedBox(height:14),TextField(controller:amount,keyboardType:const TextInputType.numberWithOptions(decimal:true),decoration:const InputDecoration(labelText:'Valor',prefixText:'R\$ ')),const Spacer(),Row(children:[Expanded(child:OutlinedButton.icon(onPressed:()=>_move('DEPOSITO'),icon:const Icon(Icons.south_west),label:const Text('Depositar'))),const SizedBox(width:8),Expanded(child:OutlinedButton.icon(onPressed:()=>_move('SAQUE'),icon:const Icon(Icons.north_east),label:const Text('Sacar')))])])));
  Widget _transferCard() => Card(child:Padding(padding:const EdgeInsets.all(18),child:Column(crossAxisAlignment:CrossAxisAlignment.stretch,children:[const Text('Transferir',style:TextStyle(fontSize:17,fontWeight:FontWeight.w800)),const SizedBox(height:14),TextField(controller:destination,keyboardType:TextInputType.number,decoration:const InputDecoration(labelText:'Conta de destino')),const SizedBox(height:10),TextField(controller:transferAmount,keyboardType:const TextInputType.numberWithOptions(decimal:true),decoration:const InputDecoration(labelText:'Valor',prefixText:'R\$ ')),const Spacer(),FilledButton.icon(onPressed:_transfer,icon:const Icon(Icons.arrow_forward),label:const Text('Fazer transferência'))])));
}
