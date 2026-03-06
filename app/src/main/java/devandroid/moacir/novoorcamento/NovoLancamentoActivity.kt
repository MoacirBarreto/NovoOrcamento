package devandroid.moacir.novoorcamento

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import devandroid.moacir.novoorcamento.database.AppDatabase
import devandroid.moacir.novoorcamento.databinding.ActivityNovoLancamentoBinding
import devandroid.moacir.novoorcamento.model.Agenda
import devandroid.moacir.novoorcamento.model.Categoria
import devandroid.moacir.novoorcamento.model.Lancamento
import devandroid.moacir.novoorcamento.model.TipoLancamento
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class NovoLancamentoActivity : AppCompatActivity() {

    private val binding by lazy { ActivityNovoLancamentoBinding.inflate(layoutInflater) }
    private val db by lazy { AppDatabase.getDatabase(this) }

    private var categorias: List<Categoria> = emptyList()
    private var dataSelecionadaMillis: Long = System.currentTimeMillis()

    private var isAgenda: Boolean = false
    private var lancamentoParaEditar: Lancamento? = null
    private var agendaParaEditar: Agenda? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        isAgenda = intent.getBooleanExtra("IS_AGENDA", false)

        // Inicializa a data atual no campo
        atualizarTextoData(Calendar.getInstance())

        inicializarComponentes()
    }

    private fun inicializarComponentes() {
        configurarCampoValor()
        configurarCampoData()
        configurarMudancaDeTipo()
        configurarBotaoSalvar()
        configurarCheckRepetir()
        carregarDadosIniciais()
    }

    private fun configurarCheckRepetir() {
        binding.chkRepetir.setOnCheckedChangeListener { _, isChecked ->
            val visibilidade = if (isChecked) View.VISIBLE else View.GONE
            binding.edtQtdMeses.visibility = visibilidade
            binding.txtMesesLabel.visibility = visibilidade

            if (!isChecked) binding.edtQtdMeses.setText("")
        }
    }

    private fun carregarDadosIniciais() {
        lifecycleScope.launch {
            val idRecebido = intent.getIntExtra("LANCAMENTO_ID", -1)

            // 1. Controle de visibilidade do container de repetição
            val mostrarContainerRepetir = isAgenda && idRecebido == -1
            binding.containerRepetir.visibility = if (mostrarContainerRepetir) View.VISIBLE else View.GONE
            if (!mostrarContainerRepetir) binding.chkRepetir.isChecked = false

            // 2. Busca categorias no Banco
            categorias = withContext(Dispatchers.IO) {
                db.orcamentoDao().listarCategorias()
            }

            // 3. Configura Spinner
            val adapter = ArrayAdapter(
                this@NovoLancamentoActivity,
                android.R.layout.simple_spinner_dropdown_item,
                categorias.map { it.nome }
            )
            binding.spinnerCategorias.adapter = adapter

            binding.spinnerCategorias.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    // Preenchimento automático apenas em novos lançamentos de despesa
                    if (idRecebido == -1 && binding.rbDespesa.isChecked) {
                        binding.edtDescricao.setText(categorias[position].nome)
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

            // 4. Fluxo de Edição ou Novo
            if (idRecebido != -1) {
                binding.txtTituloTela.text = if (isAgenda) "Editar Agenda" else "Editar Lançamento"
                if (isAgenda) carregarAgendaParaEdicao(idRecebido) else carregarLancamentoParaEdicao(idRecebido)
            } else {
                binding.txtTituloTela.text = if (isAgenda) "Nova Agenda" else "Novo Lançamento"
                atualizarVisibilidadeCategoria(binding.rbDespesa.isChecked)
            }
        }
    }

    private suspend fun carregarLancamentoParaEdicao(id: Int) {
        val lancamento = withContext(Dispatchers.IO) {
            db.orcamentoDao().listarLancamentosSemFlow().find { it.id == id }
        }
        lancamento?.let {
            lancamentoParaEditar = it
            preencherCampos(it.descricao, it.valor, it.tipo, it.data, it.categoriaID)
        }
    }

    private suspend fun carregarAgendaParaEdicao(id: Int) {
        val agenda = withContext(Dispatchers.IO) {
            db.agendaDao().listarAgendasSemFlow().find { it.id == id }
        }
        agenda?.let {
            agendaParaEditar = it
            preencherCampos(it.descricao, it.valor, it.tipo, it.data, it.categoriaID)
        }
    }

    private fun preencherCampos(desc: String, valor: Double, tipo: TipoLancamento, data: Long, catId: Int) {
        with(binding) {
            val valorEmCentavos = (valor * 100).toLong().toString()
            edtValor.setText(valorEmCentavos)
            edtDescricao.setText(desc)
            if (tipo == TipoLancamento.RECEITA) rbReceita.isChecked = true else rbDespesa.isChecked = true
            atualizarVisibilidadeCategoria(tipo == TipoLancamento.DESPESA)
            val cal = Calendar.getInstance().apply { timeInMillis = data }
            atualizarTextoData(cal)
            val posicao = categorias.indexOfFirst { it.id == catId }
            if (posicao != -1) spinnerCategorias.setSelection(posicao)
        }
    }

    private fun configurarBotaoSalvar() {
        binding.btnSalvarLancamento.setOnClickListener {
            val descricao = binding.edtDescricao.text.toString()
            val cleanValor = binding.edtValor.text.toString().replace(Regex("[^0-9]"), "")

            // Lógica de Repetição
            val repetirStr = binding.edtQtdMeses.text.toString()
            val numRepeticoes = if (isAgenda && binding.chkRepetir.isChecked && repetirStr.isNotEmpty()) {
                repetirStr.toInt().coerceAtLeast(1)
            } else 1

            if (descricao.isBlank() || cleanValor.isBlank()) {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val valorFinal = cleanValor.toDouble() / 100
            val tipo = if (binding.rbReceita.isChecked) TipoLancamento.RECEITA else TipoLancamento.DESPESA
            val catId = if (tipo == TipoLancamento.DESPESA && categorias.isNotEmpty()) {
                categorias[binding.spinnerCategorias.selectedItemPosition].id
            } else {
                categorias.find { it.nome.equals("Receita", true) }?.id ?: 1
            }

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    if (isAgenda) {
                        if (agendaParaEditar != null) {
                            val agenda = agendaParaEditar!!.copy(
                                descricao = descricao, valor = valorFinal, data = dataSelecionadaMillis, categoriaID = catId, tipo = tipo
                            )
                            db.agendaDao().upsertAgenda(agenda)
                        } else {
                            val cal = Calendar.getInstance().apply { timeInMillis = dataSelecionadaMillis }
                            for (i in 0 until numRepeticoes) {
                                val descFinal = if (numRepeticoes > 1) "$descricao (${i + 1}/$numRepeticoes)" else descricao
                                db.agendaDao().upsertAgenda(Agenda(
                                    id = 0, descricao = descFinal, valor = valorFinal, data = cal.timeInMillis, categoriaID = catId, tipo = tipo
                                ))
                                cal.add(Calendar.MONTH, 1) // Incrementa o mês para a próxima parcela
                            }
                        }
                    } else {
                        val lancamento = lancamentoParaEditar?.copy(
                            descricao = descricao, valor = valorFinal, data = dataSelecionadaMillis, categoriaID = catId, tipo = tipo
                        ) ?: Lancamento(
                            id = 0, descricao = descricao, valor = valorFinal, data = dataSelecionadaMillis, categoriaID = catId, tipo = tipo
                        )
                        db.orcamentoDao().upsertLancamento(lancamento)
                    }
                }
                Toast.makeText(this@NovoLancamentoActivity, "Salvo com sucesso!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun configurarCampoData() {
        val calendario = Calendar.getInstance().apply { timeInMillis = dataSelecionadaMillis }
        binding.edtData.setOnClickListener {
            DatePickerDialog(this, { _, ano, mes, dia ->
                calendario.set(ano, mes, dia)
                atualizarTextoData(calendario)
            }, calendario.get(Calendar.YEAR), calendario.get(Calendar.MONTH), calendario.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun atualizarTextoData(calendario: Calendar) {
        val formato = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
        binding.edtData.setText(formato.format(calendario.time))
        dataSelecionadaMillis = calendario.timeInMillis
    }

    private fun configurarMudancaDeTipo() {
        binding.radioGroupTipo.setOnCheckedChangeListener { _, checkedId ->
            val isDespesa = checkedId == R.id.rbDespesa
            atualizarVisibilidadeCategoria(isDespesa)
            if (intent.getIntExtra("LANCAMENTO_ID", -1) == -1) {
                if (!isDespesa) binding.edtDescricao.setText("Receita")
                else {
                    val pos = binding.spinnerCategorias.selectedItemPosition
                    if (pos != -1) binding.edtDescricao.setText(categorias[pos].nome)
                }
            }
        }
    }

    private fun atualizarVisibilidadeCategoria(isDespesa: Boolean) {
        binding.lblCategoria.visibility = if (isDespesa) View.VISIBLE else View.GONE
        binding.spinnerCategorias.visibility = if (isDespesa) View.VISIBLE else View.GONE
    }

    private fun configurarCampoValor() {
        binding.edtValor.addTextChangedListener(object : TextWatcher {
            private var current = ""
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.toString() != current) {
                    binding.edtValor.removeTextChangedListener(this)
                    val cleanString = s.toString().replace(Regex("[^0-9]"), "")
                    if (cleanString.isNotEmpty()) {
                        val formatted = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(cleanString.toDouble() / 100)
                        current = formatted
                        binding.edtValor.setText(formatted)
                        binding.edtValor.setSelection(formatted.length)
                    }
                    binding.edtValor.addTextChangedListener(this)
                }
            }
        })
    }
}