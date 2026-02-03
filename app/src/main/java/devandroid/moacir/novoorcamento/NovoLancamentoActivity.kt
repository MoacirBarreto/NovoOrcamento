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
    private var lancamentoParaEditar: Lancamento? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        inicializarComponentes()
    }

    private fun inicializarComponentes() {
        configurarCampoValor()
        configurarCampoData()
        configurarMudancaDeTipo()
        configurarBotaoSalvar()

        // Carregamos os dados de forma assíncrona
        carregarDadosIniciais()
    }

    private fun carregarDadosIniciais() {
        lifecycleScope.launch {
            // 1. Busca categorias em segundo plano
            categorias = withContext(Dispatchers.IO) {
                db.orcamentoDao().listarCategorias()
            }

            // 2. Configura o Spinner
            val adapter = ArrayAdapter(
                this@NovoLancamentoActivity,
                android.R.layout.simple_spinner_dropdown_item,
                categorias.map { it.nome }
            )
            binding.spinnerCategorias.adapter = adapter

            // 3. Verifica se é edição
            val idLancamento = intent.getIntExtra("LANCAMENTO_ID", -1)
            if (idLancamento != -1) {
                binding.txtTituloTela.text = "Editar Lançamento"
                carregarDadosParaEdicao(idLancamento)
            } else {
                binding.txtTituloTela.text = "Novo Lançamento"
                atualizarVisibilidadeCategoria(binding.rbDespesa.isChecked)
            }
        }
    }

    private suspend fun carregarDadosParaEdicao(id: Int) {
        // Busca o lançamento específico no banco (Thread IO)
        val lancamento = withContext(Dispatchers.IO) {
            // IMPORTANTE: Use find { it.id == id } na lista ou crie buscarPorId no DAO
            db.orcamentoDao().listarLancamentosSemFlow().find { it.id == id }
        }

        lancamento?.let {
            lancamentoParaEditar = it
            preencherCampos(it)
        }
    }

    private fun preencherCampos(lancamento: Lancamento) {
        with(binding) {
            // Formata valor (converte Double para string de centavos para o TextWatcher)
            val valorEmCentavos = (lancamento.valor * 100).toLong().toString()
            edtValor.setText(valorEmCentavos)
            edtDescricao.setText(lancamento.descricao)

            if (lancamento.tipo == TipoLancamento.RECEITA) rbReceita.isChecked = true
            else rbDespesa.isChecked = true

            atualizarVisibilidadeCategoria(lancamento.tipo == TipoLancamento.DESPESA)

            val cal = Calendar.getInstance().apply { timeInMillis = lancamento.data }
            atualizarTextoData(cal)

            val posicao = categorias.indexOfFirst { it.id == lancamento.categoriaID }
            if (posicao != -1) spinnerCategorias.setSelection(posicao)
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

            val catId = if (tipo == TipoLancamento.DESPESA && categorias.isNotEmpty()) {
                categorias[binding.spinnerCategorias.selectedItemPosition].id
            } else {
                categorias.find { it.nome.equals("Receita", true) }?.id ?: 1
            }

            val lancamento = lancamentoParaEditar?.copy(
                descricao = descricao, valor = valorFinal, data = dataSelecionadaMillis, categoriaID = catId, tipo = tipo
            ) ?: Lancamento(
                descricao = descricao, valor = valorFinal, data = dataSelecionadaMillis, categoriaID = catId, tipo = tipo
            )

            // Salva de forma assíncrona
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    db.orcamentoDao().upsertLancamento(lancamento)
                }
                Toast.makeText(this@NovoLancamentoActivity, "Sucesso!", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    // --- Métodos de UI mantidos e otimizados ---

    private fun configurarCampoData() {
        val calendario = Calendar.getInstance()
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
            atualizarVisibilidadeCategoria(checkedId == R.id.rbDespesa)
        }
    }

    private fun atualizarVisibilidadeCategoria(isDespesa: Boolean) {
        val visibilidade = if (isDespesa) View.VISIBLE else View.GONE
        binding.lblCategoria.visibility = visibilidade
        binding.spinnerCategorias.visibility = visibilidade

        if (!isDespesa) binding.edtDescricao.setText("Receita")
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