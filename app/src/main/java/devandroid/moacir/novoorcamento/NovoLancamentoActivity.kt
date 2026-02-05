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
    private var idEdicao: Long = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        isAgenda = intent.getBooleanExtra("IS_AGENDA", false)
        idEdicao = intent.getLongExtra("LANCAMENTO_ID", -1L)

        inicializarComponentes()
    }

    private fun inicializarComponentes() {
        configurarCampoValor()
        configurarCampoData()
        configurarMudancaDeTipo()
        configurarBotaoSalvar()
        configurarSelecaoCategoria()

        if (isAgenda) {
            binding.txtTituloTela.text = if (idEdicao != -1L) "Editar Agendamento" else "Novo Agendamento"
            binding.containerRepetir.visibility = if (idEdicao == -1L) View.VISIBLE else View.GONE
        } else {
            binding.txtTituloTela.text = if (idEdicao != -1L) "Editar Lançamento" else "Novo Lançamento"
            binding.containerRepetir.visibility = View.GONE
        }

        binding.chkRepetir.setOnCheckedChangeListener { _, isChecked ->
            val vis = if (isChecked) View.VISIBLE else View.GONE
            binding.edtQtdMeses.visibility = vis
            binding.txtMesesLabel.visibility = vis
        }

        carregarDadosIniciais()
    }

    private fun carregarDadosIniciais() {
        lifecycleScope.launch {
            categorias = withContext(Dispatchers.IO) { db.orcamentoDao().listarCategorias() }

            val adapter = ArrayAdapter(this@NovoLancamentoActivity, android.R.layout.simple_spinner_dropdown_item, categorias.map { it.nome })
            binding.spinnerCategorias.adapter = adapter

            if (idEdicao != -1L) {
                if (isAgenda) carregarAgendaParaEdicao(idEdicao)
                else carregarLancamentoParaEdicao(idEdicao)
            } else {
                atualizarDescricaoPelaCategoria()
            }
        }
    }

    private fun configurarSelecaoCategoria() {
        binding.spinnerCategorias.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, p2: Int, p3: Long) {
                if (idEdicao == -1L && binding.rbDespesa.isChecked) atualizarDescricaoPelaCategoria()
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun atualizarDescricaoPelaCategoria() {
        val pos = binding.spinnerCategorias.selectedItemPosition
        if (pos >= 0 && categorias.isNotEmpty()) {
            binding.edtDescricao.setText(categorias[pos].nome)
            binding.edtDescricao.setSelection(binding.edtDescricao.text?.length ?: 0)
        }
    }

    private fun configurarBotaoSalvar() {
        binding.btnSalvarLancamento.setOnClickListener {
            val descricao = binding.edtDescricao.text.toString()
            val cleanValor = binding.edtValor.text.toString().replace(Regex("[^0-9]"), "")

            if (descricao.isBlank() || cleanValor.isBlank()) {
                Toast.makeText(this, "Preencha todos os campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val valorFinal = cleanValor.toDouble() / 100
            val tipo = if (binding.rbReceita.isChecked) TipoLancamento.RECEITA else TipoLancamento.DESPESA
            val pos = binding.spinnerCategorias.selectedItemPosition
            val catId = if (tipo == TipoLancamento.DESPESA && pos != AdapterView.INVALID_POSITION && categorias.isNotEmpty()) {
                categorias[pos].id
            } else {
                categorias.find { it.nome.contains("Receita", true) }?.id ?: 1
            }

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    if (isAgenda) {
                        if (idEdicao != -1L) {
                            // EDIÇÃO DE AGENDAMENTO
                            val agenda = Agenda(
                                id = idEdicao.toInt(),
                                descricao = descricao,
                                valor = valorFinal,
                                data = dataSelecionadaMillis,
                                categoriaID = catId,
                                tipo = tipo
                            )
                            db.agendaDao().upsertAgenda(agenda)
                        } else {
                            // NOVO AGENDAMENTO (COM RECORRÊNCIA)
                            val qtd = if (binding.chkRepetir.isChecked) {
                                binding.edtQtdMeses.text.toString().toIntOrNull() ?: 1
                            } else 1

                            for (i in 0 until qtd) {
                                val cal = Calendar.getInstance().apply {
                                    timeInMillis = dataSelecionadaMillis
                                    add(Calendar.MONTH, i)
                                }
                                val d = if (qtd > 1) "$descricao (${i + 1}/$qtd)" else descricao

                                // Criando o objeto explicitamente para evitar erro de tipo
                                val novaAgenda = Agenda(
                                    id = 0,
                                    descricao = d,
                                    valor = valorFinal,
                                    data = cal.timeInMillis,
                                    categoriaID = catId,
                                    tipo = tipo
                                )
                                db.agendaDao().upsertAgenda(novaAgenda)
                            }
                        }
                    } else {
                        // LANÇAMENTO COMUM
                        val lanc = Lancamento(
                            id = if (idEdicao != -1L) idEdicao.toInt() else 0,
                            descricao = descricao,
                            valor = valorFinal,
                            data = dataSelecionadaMillis,
                            categoriaID = catId,
                            tipo = tipo
                        )
                        db.orcamentoDao().upsertLancamento(lanc)
                    }
                }
                // Feedback e fechamento SEMPRE na Main Thread (fora do withContext IO)
                Toast.makeText(this@NovoLancamentoActivity, "Salvo com sucesso!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }



    private suspend fun carregarLancamentoParaEdicao(id: Long) {
        val lanc = withContext(Dispatchers.IO) {
            db.orcamentoDao().listarLancamentosSemFlow().find { it.id.toLong() == id }
        }
        lanc?.let { preencherCampos(it.descricao, it.valor, it.data, it.tipo, it.categoriaID) }
    }

    private suspend fun carregarAgendaParaEdicao(id: Long) {
        val ag = withContext(Dispatchers.IO) { db.agendaDao().buscarPorId(id.toInt()) }
        ag?.let { preencherCampos(it.descricao, it.valor, it.data, it.tipo, it.categoriaID) }
    }

    private fun preencherCampos(d: String, v: Double, dt: Long, t: TipoLancamento, cId: Int) {
        binding.edtDescricao.setText(d)
        binding.edtValor.setText((v * 100).toLong().toString())
        if (t == TipoLancamento.RECEITA) binding.rbReceita.isChecked = true else binding.rbDespesa.isChecked = true
        val cal = Calendar.getInstance().apply { timeInMillis = dt }
        atualizarTextoData(cal)
        val p = categorias.indexOfFirst { it.id == cId }
        if (p != -1) binding.spinnerCategorias.setSelection(p)
    }

    private fun configurarCampoData() {
        val cal = Calendar.getInstance()
        binding.edtData.setOnClickListener {
            DatePickerDialog(this, { _, a, m, d ->
                cal.set(a, m, d)
                atualizarTextoData(cal)
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun atualizarTextoData(cal: Calendar) {
        binding.edtData.setText(SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(cal.time))
        dataSelecionadaMillis = cal.timeInMillis
    }

    private fun configurarMudancaDeTipo() {
        binding.radioGroupTipo.setOnCheckedChangeListener { _, id ->
            val isDesp = id == R.id.rbDespesa
            binding.lblCategoria.visibility = if (isDesp) View.VISIBLE else View.GONE
            binding.spinnerCategorias.visibility = if (isDesp) View.VISIBLE else View.GONE
            if (!isDesp) binding.edtDescricao.setText("Receita") else atualizarDescricaoPelaCategoria()
        }
    }

    private fun configurarCampoValor() {
        binding.edtValor.addTextChangedListener(object : TextWatcher {
            private var current = ""
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.toString() != current) {
                    binding.edtValor.removeTextChangedListener(this)
                    val clean = s.toString().replace(Regex("[^0-9]"), "")
                    if (clean.isNotEmpty()) {
                        current = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(clean.toDouble() / 100)
                        binding.edtValor.setText(current)
                        binding.edtValor.setSelection(current.length)
                    }
                    binding.edtValor.addTextChangedListener(this)
                }
            }
        })
    }
}