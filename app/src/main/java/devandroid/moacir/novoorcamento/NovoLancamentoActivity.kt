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
    private var idEdicao: Int = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        isAgenda = intent.getBooleanExtra("IS_AGENDA", false)
        idEdicao = intent.getIntExtra("LANCAMENTO_ID", -1)

        inicializarComponentes()
    }

    private fun inicializarComponentes() {
        configurarCampoValor()
        configurarCampoData()
        configurarMudancaDeTipo()
        configurarBotaoSalvar()
        configurarSelecaoCategoria() // Listener para preencher a descrição

        if (isAgenda) {
            binding.txtTituloTela.text = if (idEdicao != -1) "Editar Agendamento" else "Novo Agendamento"
        } else {
            binding.txtTituloTela.text = if (idEdicao != -1) "Editar Lançamento" else "Novo Lançamento"
        }

        carregarDadosIniciais()
    }

    private fun carregarDadosIniciais() {
        lifecycleScope.launch {
            // Busca categorias no Banco (Thread IO)
            categorias = withContext(Dispatchers.IO) {
                db.orcamentoDao().listarCategorias()
            }

            // Configura o Spinner
            val adapter = ArrayAdapter(
                this@NovoLancamentoActivity,
                android.R.layout.simple_spinner_dropdown_item,
                categorias.map { it.nome }
            )
            binding.spinnerCategorias.adapter = adapter

            // Se for edição, carrega os dados existentes
            if (idEdicao != -1) {
                if (isAgenda) carregarAgendaParaEdicao(idEdicao)
                else carregarLancamentoParaEdicao(idEdicao)
            } else {
                // Se for novo e Despesa, já sugere a primeira categoria
                atualizarDescricaoPelaCategoria()
            }
        }
    }

    private fun configurarSelecaoCategoria() {
        binding.spinnerCategorias.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                // Atualiza a descrição automaticamente apenas se for um novo registro
                if (idEdicao == -1 && binding.rbDespesa.isChecked) {
                    atualizarDescricaoPelaCategoria()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun atualizarDescricaoPelaCategoria() {
        val pos = binding.spinnerCategorias.selectedItemPosition
        if (pos >= 0 && categorias.isNotEmpty()) {
            binding.edtDescricao.setText(categorias[pos].nome)
            // Move o cursor para o final
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

            // Define ID da categoria (Se for receita, associamos à categoria "Receita" ou ID 1)
            val catId = if (tipo == TipoLancamento.DESPESA) {
                categorias[binding.spinnerCategorias.selectedItemPosition].id
            } else {
                categorias.find { it.nome.contains("Receita", true) }?.id ?: 1
            }

            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    if (isAgenda) {
                        val agenda = Agenda(
                            id = if (idEdicao != -1) idEdicao else 0,
                            descricao = descricao,
                            valor = valorFinal,
                            data = dataSelecionadaMillis,
                            categoriaID = catId,
                            tipo = tipo
                        )
                        db.agendaDao().upsertAgenda(agenda)
                    } else {
                        val lancamento = Lancamento(
                            id = if (idEdicao != -1) idEdicao else 0,
                            descricao = descricao,
                            valor = valorFinal,
                            data = dataSelecionadaMillis,
                            categoriaID = catId,
                            tipo = tipo
                        )
                        db.orcamentoDao().upsertLancamento(lancamento)
                    }
                }
                Toast.makeText(this@NovoLancamentoActivity, "Salvo com sucesso!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private suspend fun carregarLancamentoParaEdicao(id: Int) {
        val lancamento = withContext(Dispatchers.IO) {
            db.orcamentoDao().listarLancamentosSemFlow().find { it.id == id }
        }
        lancamento?.let { preencherCampos(it.descricao, it.valor, it.data, it.tipo, it.categoriaID) }
    }

    private suspend fun carregarAgendaParaEdicao(id: Int) {
        val agenda = withContext(Dispatchers.IO) {
            db.agendaDao().buscarPorId(id)
        }
        agenda?.let { preencherCampos(it.descricao, it.valor, it.data, it.tipo, it.categoriaID) }
    }

    private fun preencherCampos(desc: String, valor: Double, data: Long, tipo: TipoLancamento, catId: Int) {
        binding.edtDescricao.setText(desc)
        // Converte Double para String de centavos para o TextWatcher formatar
        val valorFormatado = (valor * 100).toLong().toString()
        binding.edtValor.setText(valorFormatado)

        if (tipo == TipoLancamento.RECEITA) binding.rbReceita.isChecked = true else binding.rbDespesa.isChecked = true

        val cal = Calendar.getInstance().apply { timeInMillis = data }
        atualizarTextoData(cal)

        val posicao = categorias.indexOfFirst { it.id == catId }
        if (posicao != -1) binding.spinnerCategorias.setSelection(posicao)
    }

    private fun configurarCampoData() {
        val calendario = Calendar.getInstance()
        binding.edtData.setOnClickListener {
            DatePickerDialog(this, { _, ano, mes, dia ->
                calendario.set(ano, mes, dia)
                atualizarTextoData(calendario)
            }, calendario.get(Calendar.YEAR), calendario.get(Calendar.MONTH), calendario.get(Calendar.DAY_OF_MONTH)).show()
        }
    }

    private fun atualizarTextoData(cal: Calendar) {
        binding.edtData.setText(SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR")).format(cal.time))
        dataSelecionadaMillis = cal.timeInMillis
    }

    private fun configurarMudancaDeTipo() {
        binding.radioGroupTipo.setOnCheckedChangeListener { _, checkedId ->
            val isDespesa = checkedId == R.id.rbDespesa
            binding.lblCategoria.visibility = if (isDespesa) View.VISIBLE else View.GONE
            binding.spinnerCategorias.visibility = if (isDespesa) View.VISIBLE else View.GONE

            if (!isDespesa) {
                binding.edtDescricao.setText("Receita")
            } else {
                atualizarDescricaoPelaCategoria()
            }
        }
    }

    private fun configurarCampoValor() {
        binding.edtValor.addTextChangedListener(object : TextWatcher {
            private var current = ""
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (s.toString() != current) {
                    binding.edtValor.removeTextChangedListener(this)
                    val clean = s.toString().replace(Regex("[^0-9]"), "")
                    if (clean.isNotEmpty()) {
                        val formatted = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(clean.toDouble() / 100)
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