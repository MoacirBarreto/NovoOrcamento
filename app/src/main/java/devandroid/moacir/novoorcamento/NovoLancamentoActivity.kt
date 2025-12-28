package devandroid.moacir.novoorcamento

import android.app.DatePickerDialog
import android.os.Bundle
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import android.text.Editable
import android.text.TextWatcher
import java.text.NumberFormat

class NovoLancamentoActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNovoLancamentoBinding
    private lateinit var categorias: List<Categoria>
    private var dataSelecionadaMillis: Long = System.currentTimeMillis()

    // Variável para guardar o lançamento que estamos editando
    private var lancamentoParaEditar: Lancamento? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNovoLancamentoBinding.inflate(layoutInflater)
        setContentView(binding.root)

        configurarCampoValor()
        carregarCategoriasNoSpinner()
        configurarCampoData()
        configurarMudancaDeTipo()
        configurarBotaoSalvar()


        // --- LÓGICA DE EDIÇÃO ---
        val idLancamento = intent.getIntExtra("LANCAMENTO_ID", -1)
        if (idLancamento != -1) {
            // Modo Edição
            binding.txtTituloTela.text = "Editar Lançamento"
            carregarDadosParaEdicao(idLancamento)
        } else {
            // Modo Criação
            binding.txtTituloTela.text = "Novo Lançamento"
            // Força a primeira checagem de visibilidade da categoria
            atualizarVisibilidadeCategoria(binding.rbDespesa.isChecked)
        }
    }

    private fun carregarDadosParaEdicao(id: Int) {
        val db = AppDatabase.getDatabase(this)
        // Precisamos buscar em outra thread, mas para simplificar:
        lancamentoParaEditar = db.orcamentoDao().listarLancamentos().find { it.id == id }

        lancamentoParaEditar?.let { lancamento ->

            val formatoMoeda = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
            binding.edtValor.setText(formatoMoeda.format(lancamento.valor))
            // -----------------------------------------------------

            binding.edtDescricao.setText(lancamento.descricao)
            


            binding.edtValor.setText(lancamento.valor.toString())
            binding.edtDescricao.setText(lancamento.descricao)

            // Configura a data
            val calendario = Calendar.getInstance()
            calendario.timeInMillis = lancamento.data
            dataSelecionadaMillis = lancamento.data
            atualizarTextoData(calendario)

            // Configura o tipo (Receita/Despesa)
            if (lancamento.tipo == TipoLancamento.RECEITA) {
                binding.rbReceita.isChecked = true
            } else {
                binding.rbDespesa.isChecked = true
            }
            atualizarVisibilidadeCategoria(lancamento.tipo == TipoLancamento.DESPESA)

            // Seleciona a categoria correta no Spinner
            val posicaoCategoria = categorias.indexOfFirst { it.id == lancamento.categoriaID }
            if (posicaoCategoria != -1) {
                binding.spinnerCategorias.setSelection(posicaoCategoria)
            }
        }
    }


    private fun configurarCampoData() {
        val calendario = Calendar.getInstance()
        atualizarTextoData(calendario) // Define data inicial

        binding.edtData.setOnClickListener {
            DatePickerDialog(
                this,
                { _, ano, mes, dia ->
                    calendario.set(ano, mes, dia)
                    atualizarTextoData(calendario)
                },
                calendario.get(Calendar.YEAR),
                calendario.get(Calendar.MONTH),
                calendario.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    // Função auxiliar para atualizar o campo de data
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

    // Função para mostrar/esconder a categoria e preencher a descrição
    private fun atualizarVisibilidadeCategoria(isDespesa: Boolean) {
        if (isDespesa) {
            binding.lblCategoria.visibility = View.VISIBLE
            binding.spinnerCategorias.visibility = View.VISIBLE
            // Preenche descrição com o item atual do spinner
            val posicao = binding.spinnerCategorias.selectedItemPosition
            if (posicao >= 0 && categorias.isNotEmpty()) {
                binding.edtDescricao.setText(categorias[posicao].nome)
            }
        } else { // É Receita
            binding.lblCategoria.visibility = View.GONE
            binding.spinnerCategorias.visibility = View.GONE
            binding.edtDescricao.setText("Receita")
        }
    }


    private fun carregarCategoriasNoSpinner() {
        val db = AppDatabase.getDatabase(this)
        categorias = db.orcamentoDao().listarCategorias()
        val nomesCategorias = categorias.map { it.nome }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, nomesCategorias)
        binding.spinnerCategorias.adapter = adapter

        binding.spinnerCategorias.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (binding.rbDespesa.isChecked) {
                    binding.edtDescricao.setText(categorias[position].nome)
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun configurarBotaoSalvar() {
        binding.btnSalvarLancamento.setOnClickListener {
            val descricao = binding.edtDescricao.text.toString()
            val valorTexto = binding.edtValor.text.toString()

            // 1. Limpa a formatação para obter o número real
            val cleanString = valorTexto.replace(Regex("[^0-9]"), "")

            // Verifica se tem algo digitado
            if (descricao.isNotEmpty() && cleanString.isNotEmpty()) {

                // 2. Calcula o valor correto (divide por 100 por causa dos centavos)
                val valorFinal = cleanString.toDouble() / 100

                val isReceita = binding.rbReceita.isChecked
                val tipo = if (isReceita) TipoLancamento.RECEITA else TipoLancamento.DESPESA

                // 3. Lógica da Categoria (Corrigida)
                // Se for Despesa, pega do Spinner. Se for Receita, tenta achar "Outros" ou pega a primeira.
                val catId = if (!isReceita) {
                    // É DESPESA
                    if (categorias.isNotEmpty()) {
                        categorias[binding.spinnerCategorias.selectedItemPosition].id
                    } else {
                        1 // Fallback seguro
                    }
                } else {
                    // É RECEITA (geralmente não tem categoria específica neste app, usa um padrão)
                    // Tenta achar categoria "Outros" ou pega a primeira disponível
                    categorias.find { it.nome.equals("Receita", ignoreCase = true) }?.id ?: categorias.firstOrNull()?.id ?: 1
                }

                val db = AppDatabase.getDatabase(this)

                if (lancamentoParaEditar == null) {
                    // --- MODO CRIAÇÃO ---
                    val novoLancamento = Lancamento(
                        descricao = descricao,
                        valor = valorFinal, // Usa o valor calculado corretamente
                        data = dataSelecionadaMillis,
                        categoriaID = catId,
                        tipo = tipo
                    )
                    db.orcamentoDao().inserirLancamento(novoLancamento)
                    Toast.makeText(this, "Lançamento Salvo!", Toast.LENGTH_SHORT).show()
                } else {
                    // --- MODO ATUALIZAÇÃO ---
                    val lancamentoAtualizado = lancamentoParaEditar!!.copy(
                        descricao = descricao,
                        valor = valorFinal, // Usa o valor calculado corretamente
                        data = dataSelecionadaMillis,
                        categoriaID = catId,
                        tipo = tipo
                    )
                    db.orcamentoDao().atualizarLancamento(lancamentoAtualizado)
                    Toast.makeText(this, "Lançamento Atualizado!", Toast.LENGTH_SHORT).show()
                }
                finish() // Fecha a tela e volta para a principal

            } else {
                Toast.makeText(this, "Preencha descrição e valor", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Adicione esta função na sua Activity
    private fun configurarCampoValor() {
        binding.edtValor.addTextChangedListener(object : TextWatcher {
            private var current = ""

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (s.toString() != current) {
                    // Remove o listener para não entrar em loop infinito
                    binding.edtValor.removeTextChangedListener(this)

                    // 1. Limpa tudo que não for número
                    val cleanString = s.toString().replace(Regex("[^0-9]"), "")

                    if (cleanString.isNotEmpty()) {
                        // 2. Transforma em double e divide por 100 para ter os centavos
                        val parsed = cleanString.toDouble()
                        val formatted = NumberFormat.getCurrencyInstance(Locale("pt", "BR")).format(parsed / 100)

                        current = formatted
                        binding.edtValor.setText(formatted)
                        // 3. Move o cursor para o final
                        binding.edtValor.setSelection(formatted.length)
                    } else {
                        current = ""
                        binding.edtValor.setText("")
                    }

                    // Adiciona o listener de volta
                    binding.edtValor.addTextChangedListener(this)
                }
            }
        })
    }
}
