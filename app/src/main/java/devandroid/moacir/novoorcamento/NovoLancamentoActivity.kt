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
import devandroid.moacir.novoorcamento.database.AppDatabase
import devandroid.moacir.novoorcamento.databinding.ActivityNovoLancamentoBinding
import devandroid.moacir.novoorcamento.model.Categoria
import devandroid.moacir.novoorcamento.model.Lancamento
import devandroid.moacir.novoorcamento.model.TipoLancamento
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class NovoLancamentoActivity : AppCompatActivity() {

    private val binding by lazy { ActivityNovoLancamentoBinding.inflate(layoutInflater) }
    private val db by lazy { AppDatabase.getDatabase(this) }

    private lateinit var categorias: List<Categoria>
    private var dataSelecionadaMillis: Long = System.currentTimeMillis()
    private var lancamentoParaEditar: Lancamento? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        inicializarComponentes()
        verificarModoEdicao()
    }

    private fun inicializarComponentes() {
        configurarCampoValor()
        carregarCategoriasNoSpinner()
        configurarCampoData()
        configurarMudancaDeTipo()
        configurarBotaoSalvar()
    }

    private fun verificarModoEdicao() {
        val idLancamento = intent.getIntExtra("LANCAMENTO_ID", -1)
        if (idLancamento != -1) {
            binding.txtTituloTela.text = "Editar Lançamento"
            carregarDadosParaEdicao(idLancamento)
        } else {
            binding.txtTituloTela.text = "Novo Lançamento"
            atualizarVisibilidadeCategoria(binding.rbDespesa.isChecked)
        }
    }

    private fun carregarDadosParaEdicao(id: Int) {
        // Busca na lista já carregada ou direto no banco (otimizado para usar o ID direto)
        lancamentoParaEditar = db.orcamentoDao().listarLancamentos().find { it.id == id }

        lancamentoParaEditar?.let { lancamento ->
            with(binding) {
                // Converte para centavos para o TextWatcher formatar corretamente
                val valorEmCentavos = (lancamento.valor * 100).toLong().toString()
                edtValor.setText(valorEmCentavos)
                edtDescricao.setText(lancamento.descricao)

                // Tipo e Visibilidade
                if (lancamento.tipo == TipoLancamento.RECEITA) rbReceita.isChecked = true
                else rbDespesa.isChecked = true

                atualizarVisibilidadeCategoria(lancamento.tipo == TipoLancamento.DESPESA)

                // Data
                val cal = Calendar.getInstance().apply { timeInMillis = lancamento.data }
                atualizarTextoData(cal)

                // Categoria no Spinner
                val posicao = categorias.indexOfFirst { it.id == lancamento.categoriaID }
                if (posicao != -1) spinnerCategorias.setSelection(posicao)
            }
        }
    }

    private fun configurarCampoData() {
        val calendario = Calendar.getInstance()
        atualizarTextoData(calendario)

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
        with(binding) {
            val visibilidade = if (isDespesa) View.VISIBLE else View.GONE
            lblCategoria.visibility = visibilidade
            spinnerCategorias.visibility = visibilidade

            if (isDespesa) {
                val pos = spinnerCategorias.selectedItemPosition
                if (pos >= 0 && categorias.isNotEmpty()) edtDescricao.setText(categorias[pos].nome)
            } else {
                edtDescricao.setText("Receita")
            }
        }
    }

    private fun carregarCategoriasNoSpinner() {
        categorias = db.orcamentoDao().listarCategorias()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, categorias.map { it.nome })
        binding.spinnerCategorias.adapter = adapter

        binding.spinnerCategorias.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, position: Int, p3: Long) {
                if (binding.rbDespesa.isChecked) binding.edtDescricao.setText(categorias[position].nome)
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
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

            // Definição de ID de Categoria
            val catId = if (tipo == TipoLancamento.DESPESA && categorias.isNotEmpty()) {
                categorias[binding.spinnerCategorias.selectedItemPosition].id
            } else {
                // Busca categoria padrão para Receita ou ID 1
                categorias.find { it.nome.equals("Receita", true) }?.id ?: categorias.firstOrNull()?.id ?: 1
            }

            // Criação do objeto (Update ou New)
            val lancamento = lancamentoParaEditar?.copy(
                descricao = descricao, valor = valorFinal, data = dataSelecionadaMillis, categoriaID = catId, tipo = tipo
            ) ?: Lancamento(
                descricao = descricao, valor = valorFinal, data = dataSelecionadaMillis, categoriaID = catId, tipo = tipo
            )

            if (lancamentoParaEditar == null) db.orcamentoDao().inserirLancamento(lancamento)
            else db.orcamentoDao().atualizarLancamento(lancamento)

            Toast.makeText(this, "Sucesso!", Toast.LENGTH_SHORT).show()
            finish()
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