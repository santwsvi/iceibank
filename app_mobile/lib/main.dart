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
class IceiApp extends StatelessWidget { const IceiApp({super.key}); @override Widget build(BuildContext context) => MaterialApp(title: 'ICEIBank', theme: ThemeData(colorSchemeSeed: Colors.indigo, useMaterial3: true), home: const BankPage()); }

class BankPage extends StatefulWidget { const BankPage({super.key}); @override State<BankPage> createState() => _BankPageState(); }
class _BankPageState extends State<BankPage> {
  LocalStore? db; String? token, message; int base = 0; int? leader; int? accountId; double? balance; List<int> vector = [0,0,0]; List<Map<String,Object?>> pending = [];
  final user = TextEditingController(text: 'aluno'), pass = TextEditingController(text: 'senha123'), account = TextEditingController(), amount = TextEditingController(), destination = TextEditingController();
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
  Future<void> _transfer() async { try {final id=accountId??int.parse(account.text); final dest=int.parse(destination.text); final data=await _request('POST','/transferencias',body:{'idOrigem':id,'idDestino':dest,'valor':double.parse(amount.text)}); setState(()=>message=data['mensagem']); await _getBalance();}catch(e){setState(()=>message='$e');} }
  @override void dispose(){electionTimer?.cancel();connection?.cancel();for(final c in [user,pass,account,amount,destination]){c.dispose();}super.dispose();}
  @override Widget build(BuildContext context){final logged=token!=null; return Scaffold(appBar:AppBar(title:const Text('ICEIBank Mobile')),body:!logged?ListView(padding:const EdgeInsets.all(20),children:[DropdownButton<int>(value:base,items:List.generate(3,(i)=>DropdownMenuItem(value:i,child:Text('Agência de entrada $i'))),onChanged:(v)=>setState(()=>base=v!)),TextField(controller:user,decoration:const InputDecoration(labelText:'Usuário')),TextField(controller:pass,obscureText:true,decoration:const InputDecoration(labelText:'Senha')),FilledButton(onPressed:_login,child:const Text('Entrar')),if(message!=null)Text(message!)]) : ListView(padding:const EdgeInsets.all(20),children:[Card(child:ListTile(title:Text(leader==null?'Líder: indisponível':'Agência líder: $leader'),subtitle:const Text('Atualização automática a cada 4 segundos'))),TextField(controller:account,keyboardType:TextInputType.number,decoration:const InputDecoration(labelText:'ID da conta')),FilledButton(onPressed:_getBalance,child:const Text('Consultar saldo')),if(balance!=null)Text('Saldo${pending.isNotEmpty?' provisório':''}: R\$ ${balance!.toStringAsFixed(2)}'),TextField(controller:amount,keyboardType:const TextInputType.numberWithOptions(decimal:true),decoration:const InputDecoration(labelText:'Valor')),Wrap(spacing:8,children:[OutlinedButton(onPressed:()=>_move('DEPOSITO'),child:const Text('Depositar')),OutlinedButton(onPressed:()=>_move('SAQUE'),child:const Text('Sacar'))]),TextField(controller:destination,keyboardType:TextInputType.number,decoration:const InputDecoration(labelText:'Conta destino')),FilledButton(onPressed:_transfer,child:const Text('Transferir')),if(message!=null)Padding(padding:const EdgeInsets.all(8),child:Text(message!)),Text('Operações pendentes: ${pending.length}'),...pending.map((op)=>ListTile(leading:const Icon(Icons.sync_problem),title:Text('${op['type']} R\$ ${op['amount']}'),subtitle:Text(op['conflict']==null?'Aguardando sincronização':'Conflito: revise antes de descartar')))]));}
}
